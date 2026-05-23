package com.noexcs.indolent.agent.memory

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer

class EmbeddingModel {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var embeddingDim: Int = -1

    fun isReady(): Boolean = session != null

    fun load(context: Context, modelAssetPath: String) {
        val bytes = context.assets.open(modelAssetPath).use { it.readBytes() }
        env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
            addCPU(true)
        }
        session = env!!.createSession(bytes, opts)

        // Infer embedding dimension from output shape
        val outputInfo = session!!.getOutputInfo()
        for ((_, nodeInfo) in outputInfo) {
            val info = nodeInfo.info
            if (info is TensorInfo) {
                val shape = info.shape
                // shape is either [1, dim] (pooled) or [1, seq_len, dim] (raw)
                if (shape.size == 2) {
                    embeddingDim = shape[1].toInt()
                } else if (shape.size == 3) {
                    embeddingDim = shape[2].toInt()
                }
                break
            }
        }
        if (embeddingDim <= 0) embeddingDim = 384

        bytes.fill(0)
        Lumberjack.i("EmbeddingModel", "Model loaded, dim=$embeddingDim")
    }

    suspend fun embed(
        inputIds: LongArray,
        attentionMask: LongArray,
        tokenTypeIds: LongArray
    ): FloatArray = withContext(Dispatchers.Default) {
        val s = checkNotNull(session) { "Model not loaded" }
        val ortEnv = checkNotNull(env) { "Environment not initialized" }

        val seqLen = inputIds.size
        val inputShape = longArrayOf(1L, seqLen.toLong())

        val inputIdsTensor = OnnxTensor.createTensor(
            ortEnv, LongBuffer.wrap(inputIds), inputShape
        )
        val attentionMaskTensor = OnnxTensor.createTensor(
            ortEnv, LongBuffer.wrap(attentionMask), inputShape
        )
        val tokenTypeIdsTensor = OnnxTensor.createTensor(
            ortEnv, LongBuffer.wrap(tokenTypeIds), inputShape
        )

        try {
            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            val output = s.run(inputs).use { result ->
                result.get(0)
            }
            val outputInfo = output.info
            val value = output.value

            if (outputInfo is TensorInfo && outputInfo.shape.size <= 2) {
                // Already pooled: [1, dim] — extract directly
                val batch = value as Array<*>
                batch[0] as FloatArray
            } else {
                // Raw hidden states: [1, seq_len, hidden_dim] — mean pooling
                val batch = value as Array<*>
                val hiddenState = batch[0] as Array<*>
                meanPool(hiddenState, seqLen)
            }
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
        }
    }

    private fun meanPool(hiddenState: Array<*>, seqLen: Int): FloatArray {
        val vec = FloatArray(embeddingDim)
        var count = 0
        for (t in 0 until seqLen) {
            val tokenVec = hiddenState[t] as FloatArray
            for (d in 0 until embeddingDim) {
                vec[d] += tokenVec[d]
            }
            count++
        }
        for (d in 0 until embeddingDim) {
            vec[d] /= count.toFloat()
        }
        return vec
    }

    fun close() {
        session?.close()
        session = null
        env?.close()
        env = null
    }

    fun dim(): Int = embeddingDim
}
