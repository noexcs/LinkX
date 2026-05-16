package com.noexcs.indolent.agent.memory

import org.json.JSONObject

/**
 * Pure Kotlin WordPiece tokenizer compatible with HuggingFace tokenizer.json format.
 * Supports BERT-family models (all-MiniLM-L6-v2, etc.).
 */
class WordPieceTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val unkToken: String,
    private val clsToken: String,
    private val sepToken: String,
    private val padToken: String,
    private val unkTokenId: Int,
    private val clsTokenId: Int,
    private val sepTokenId: Int,
    private val padTokenId: Int,
    private val maxInputCharsPerWord: Int,
    private val lowercase: Boolean
) {
    data class TokenizerOutput(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray
    )

    fun encode(text: String, maxLength: Int = 128): TokenizerOutput {
        // Normalize
        val normalized = if (lowercase) text.lowercase() else text

        // Pre-tokenize: split on whitespace and punctuation
        val preTokens = preTokenize(normalized)

        // WordPiece tokenize each pre-token
        val tokenIds = mutableListOf(clsTokenId)
        for (token in preTokens) {
            if (token.isEmpty()) continue
            tokenizeWord(token, tokenIds)
        }
        tokenIds.add(sepTokenId)

        // Truncate if needed (reserve last position for [SEP])
        val effectiveIds = if (tokenIds.size > maxLength) {
            tokenIds.subList(0, maxLength - 1).also { it.add(sepTokenId) }
        } else {
            tokenIds
        }

        val seqLen = effectiveIds.size
        val inputIds = LongArray(maxLength)
        val attentionMask = LongArray(maxLength)
        val tokenTypeIds = LongArray(maxLength)

        for (i in 0 until seqLen) {
            inputIds[i] = effectiveIds[i].toLong()
            attentionMask[i] = 1L
        }
        // Remaining positions stay as 0 (padding)

        return TokenizerOutput(inputIds, attentionMask, tokenTypeIds)
    }

    private fun tokenizeWord(word: String, output: MutableList<Int>) {
        if (word.length > maxInputCharsPerWord) {
            output.add(unkTokenId)
            return
        }

        // Check if the full word is in vocab
        vocab[word]?.let {
            output.add(it)
            return
        }

        // WordPiece sub-word tokenization
        var start = 0
        val chars = word.toCharArray()
        var isBad = false

        while (start < chars.size) {
            var end = chars.size
            var found = false

            while (start < end) {
                val subStr = if (start == 0) {
                    String(chars, start, end - start)
                } else {
                    "##" + String(chars, start, end - start)
                }

                vocab[subStr]?.let {
                    output.add(it)
                    found = true
                    start = end
                    break
                }
                end--
            }

            if (!found) {
                isBad = true
                break
            }
        }

        if (isBad) {
            output.add(unkTokenId)
        }
    }

    private fun preTokenize(text: String): List<String> {
        // Split on whitespace first, then split punctuation from each token
        val result = mutableListOf<String>()
        for (token in text.split(Regex("\\s+"))) {
            splitPunctuation(token, result)
        }
        return result
    }

    private fun splitPunctuation(token: String, output: MutableList<String>) {
        // BERT-style punctuation splitting
        val chars = token.toCharArray()
        var i = 0
        val current = StringBuilder()

        while (i < chars.size) {
            val c = chars[i]
            if (isPunctuation(c)) {
                if (current.isNotEmpty()) {
                    output.add(current.toString())
                    current.clear()
                }
                output.add(c.toString())
            } else {
                current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) {
            output.add(current.toString())
        }
    }

    private fun isPunctuation(c: Char): Boolean {
        return c in setOf(
            '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+',
            ',', '-', '.', '/', ':', ';', '<', '=', '>', '?', '@',
            '[', '\\', ']', '^', '_', '`', '{', '|', '}', '~'
        )
    }

    companion object {
        fun load(tokenizerJson: String): WordPieceTokenizer {
            val root = JSONObject(tokenizerJson)

            // Parse vocab
            val model = root.getJSONObject("model")
            val vocabJson = model.getJSONObject("vocab")
            val vocab = mutableMapOf<String, Int>()
            for (key in vocabJson.keys()) {
                vocab[key] = vocabJson.getInt(key)
            }

            val unkToken = model.optString("unk_token", "[UNK]")
            val continuingSubwordPrefix = model.optString("continuing_subword_prefix", "##")
            val maxInputCharsPerWord = model.optInt("max_input_chars_per_word", 100)

            // Detect special tokens from added_tokens or fall back to BERT defaults
            val addedTokens = root.optJSONArray("added_tokens")
            var clsToken = "[CLS]"
            var sepToken = "[SEP]"
            var padToken = "[PAD]"

            if (addedTokens != null) {
                for (i in 0 until addedTokens.length()) {
                    val token = addedTokens.getJSONObject(i)
                    val content = token.getString("content")
                    val special = token.optBoolean("special", false)
                    if (special) {
                        val id = token.getInt("id")
                        when {
                            id == 101 || content == "[CLS]" -> clsToken = content
                            id == 102 || content == "[SEP]" -> sepToken = content
                            id == 0 || content == "[PAD]" -> padToken = content
                        }
                    }
                }
            }

            val clsTokenId = vocab[clsToken] ?: 101
            val sepTokenId = vocab[sepToken] ?: 102
            val padTokenId = vocab[padToken] ?: 0
            val unkTokenId = vocab[unkToken] ?: 100

            // Check if normalizer indicates lowercase
            val normalizer = root.optJSONObject("normalizer")
            val lowercase = normalizer?.optString("type") == "Lowercase" ||
                    normalizer?.optBoolean("lowercase", false) == true ||
                    clsToken == "[CLS]" // BERT default

            return WordPieceTokenizer(
                vocab = vocab,
                unkToken = unkToken,
                clsToken = clsToken,
                sepToken = sepToken,
                padToken = padToken,
                unkTokenId = unkTokenId,
                clsTokenId = clsTokenId,
                sepTokenId = sepTokenId,
                padTokenId = padTokenId,
                maxInputCharsPerWord = maxInputCharsPerWord,
                lowercase = lowercase
            )
        }
    }
}
