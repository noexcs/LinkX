#!/usr/bin/env python3
"""
Export a sentence-transformers model to ONNX for Android ONNX Runtime.

Uses torch.onnx.export directly — avoids optimum dependency conflicts.

Requirements:
    pip install torch transformers sentence-transformers onnx onnxruntime

Usage:
    python scripts/export_onnx.py

Output (placed in app/src/main/assets/models/):
    model.onnx        ONNX model with last_hidden_state output (3D: mean pooling in Kotlin)
    tokenizer.json    HuggingFace WordPiece tokenizer configuration
"""

import json
import os
import torch
from pathlib import Path

MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"
# For multilingual: "intfloat/multilingual-e5-small"

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "models"
SEQ_LEN = 128  # Fixed sequence length for ONNX export


def export():
    from transformers import AutoTokenizer, AutoModel

    print(f"Loading model: {MODEL_NAME}")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModel.from_pretrained(MODEL_NAME)
    model.eval()

    print("Exporting to ONNX...")
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # Create dummy inputs
    dummy_text = "sample text for tracing"
    encoded = tokenizer(
        dummy_text,
        padding="max_length",
        truncation=True,
        max_length=SEQ_LEN,
        return_tensors="pt",
    )

    input_ids = encoded["input_ids"]
    attention_mask = encoded["attention_mask"]
    token_type_ids = encoded["token_type_ids"]

    onnx_path = OUTPUT_DIR / "model.onnx"

    # Export with dynamo=False to produce a single-file (monolithic) ONNX model.
    # The dynamo-based exporter (default in PyTorch 2.x) uses external data format
    # which is incompatible with Android ONNX Runtime.
    torch.onnx.export(
        model,
        (input_ids, attention_mask, token_type_ids),
        str(onnx_path),
        input_names=["input_ids", "attention_mask", "token_type_ids"],
        output_names=["last_hidden_state", "pooler_output"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "sequence"},
            "attention_mask": {0: "batch", 1: "sequence"},
            "token_type_ids": {0: "batch", 1: "sequence"},
            "last_hidden_state": {0: "batch", 1: "sequence"},
            "pooler_output": {0: "batch"},
        },
        opset_version=18,
        do_constant_folding=True,
        dynamo=False,
    )

    print(f"  model.onnx: {onnx_path.stat().st_size / 1024 / 1024:.1f} MB")

    # Export tokenizer
    tokenizer_path = OUTPUT_DIR / "tokenizer.json"
    tokenizer.save_pretrained(str(OUTPUT_DIR))
    print(f"  tokenizer.json: {tokenizer_path.stat().st_size / 1024 / 1024:.1f} MB")

    # Clean up extra files
    for extra in OUTPUT_DIR.glob("*"):
        if extra.name not in ("model.onnx", "tokenizer.json"):
            extra.unlink()
            print(f"  Removed: {extra.name}")

    # Validate
    import onnxruntime as ort
    session = ort.InferenceSession(str(onnx_path))
    print("\nONNX Model I/O:")
    for inp in session.get_inputs():
        print(f"  Input:  {inp.name} {inp.shape}")
    for out in session.get_outputs():
        print(f"  Output: {out.name} {out.shape}")

    # Test inference
    print("\nRunning test inference...")
    result = session.run(
        None,
        {
            "input_ids": input_ids.numpy(),
            "attention_mask": attention_mask.numpy(),
            "token_type_ids": token_type_ids.numpy(),
        },
    )
    output = result[0]
    print(f"  Output shape: {output.shape} (expected: [1, {SEQ_LEN}, dim])")

    # Verify tokenizer
    with open(tokenizer_path) as f:
        tok_data = json.load(f)
    vocab_size = len(tok_data.get("model", {}).get("vocab", {}))
    print(f"  Tokenizer vocab size: {vocab_size}")
    print(f"\nDone! Files ready in: {OUTPUT_DIR}")


if __name__ == "__main__":
    export()
