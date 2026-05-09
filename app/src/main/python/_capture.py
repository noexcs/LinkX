import sys
import io


def run(code):
    out = io.StringIO()
    err = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = out, err
    try:
        exec(code)
        sys.stdout.flush()
        sys.stderr.flush()
        return out.getvalue(), err.getvalue()
    except Exception as e:
        sys.stdout.flush()
        sys.stderr.flush()
        raise RuntimeError(
            f"{out.getvalue()}\n{err.getvalue()}\n{type(e).__name__}: {e}"
        )
    finally:
        sys.stdout, sys.stderr = old_out, old_err
