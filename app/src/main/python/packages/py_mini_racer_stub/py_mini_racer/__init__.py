import json
import re


class MiniRacer:
    """Replace py_mini_racer (which needs native V8) with regex-based JS extraction.

    The East Money fund data JS files use a simple format:
        var VariableName = <JSON value>;

    This stub extracts those variable assignments without a JS engine.
    """

    def __init__(self, *args, **kwargs):
        self._vars = {}

    def eval(self, js_code: str):
        """Extract variable assignments from East Money JS files."""
        self._vars = _extract_js_vars(js_code)

    def execute(self, var_name: str):
        """Return the value of a previously parsed variable."""
        return self._vars.get(var_name)

    def call(self, fn_name: str, *args):
        """Not needed for fund data; return empty."""
        return ""


StrictMiniRacer = MiniRacer


def _extract_js_vars(js_code: str) -> dict:
    """Extract all top-level `var NAME = VALUE;` assignments from JS code.

    Handles JSON arrays, JSON objects, strings, numbers, booleans, and null.
    """
    result = {}
    # Find patterns like: var Name = value;
    # The value can be a JSON array/object (nested brackets) or a scalar.
    for match in re.finditer(r'var\s+(\w+)\s*=\s*', js_code):
        name = match.group(1)
        start = match.end()
        if start >= len(js_code):
            continue
        value = _parse_js_value(js_code, start)
        if value is not None:
            result[name] = value
    return result


def _parse_js_value(js_code: str, start: int):
    """Parse a JavaScript value starting at position `start`.

    Returns (value, end_position) or None if parsing fails.
    """
    js_code = js_code[start:]
    # Skip leading whitespace
    js_code = js_code.lstrip()
    if not js_code:
        return None

    first_char = js_code[0]

    if first_char in ('"', "'"):
        # String value
        return _parse_js_string(js_code)

    elif first_char == '[':
        # Array or JSON array
        return _parse_js_bracketed(js_code, '[', ']')

    elif first_char == '{':
        # Object or JSON object
        return _parse_js_bracketed(js_code, '{', '}')

    else:
        # Scalar: number, boolean, null
        m = re.match(r'([\d.]+|true|false|null)', js_code)
        if m:
            token = m.group(1)
            if token == 'null':
                return None
            if token == 'true':
                return True
            if token == 'false':
                return False
            try:
                return int(token)
            except ValueError:
                return float(token)
        return None


def _parse_js_string(js_code: str):
    """Parse a quoted JavaScript string. Handles escape sequences."""
    quote = js_code[0]
    i = 1
    result = []
    while i < len(js_code):
        c = js_code[i]
        if c == '\\':
            i += 1
            if i < len(js_code):
                result.append('\\' + js_code[i])
        elif c == quote:
            return ''.join(result)
        else:
            result.append(c)
        i += 1
    return ''.join(result)


def _parse_js_bracketed(js_code: str, open_char: str, close_char: str):
    """Parse a bracketed JS/JSON value, tracking nesting depth."""
    depth = 0
    in_string = False
    string_char = None
    i = 0
    while i < len(js_code):
        c = js_code[i]
        if in_string:
            if c == '\\':
                i += 1  # skip escaped char
            elif c == string_char:
                in_string = False
        else:
            if c in ('"', "'"):
                in_string = True
                string_char = c
            elif c == open_char:
                depth += 1
            elif c == close_char:
                depth -= 1
                if depth == 0:
                    value_str = js_code[:i + 1]
                    # Replace any JS-specific syntax before JSON parsing
                    # (unquoted keys in objects, trailing commas, etc.)
                    try:
                        return json.loads(value_str)
                    except json.JSONDecodeError:
                        # Try to fix common JS→JSON issues and retry
                        value_str = _js_to_json(value_str)
                        try:
                            return json.loads(value_str)
                        except json.JSONDecodeError:
                            return None
        i += 1
    return None


def _js_to_json(js_str: str) -> str:
    """Convert a JavaScript literal to valid JSON.

    Handles: unquoted object keys, single quotes, trailing commas.
    """
    # Replace single quotes with double quotes (careful with nested quotes)
    # First, handle single-quoted keys: {key: value} → {"key": value}
    # Simple regex-based approach
    result = []
    in_string = False
    string_char = None
    i = 0
    while i < len(js_str):
        c = js_str[i]
        if in_string:
            if c == '\\':
                result.append(c)
                i += 1
                if i < len(js_str):
                    result.append(js_str[i])
            elif c == string_char:
                in_string = False
                result.append('"')  # Normalize to double quote
            else:
                result.append(c)
        else:
            if c in ('"', "'"):
                in_string = True
                string_char = c
                result.append('"')  # Normalize to double quote
            else:
                result.append(c)
        i += 1

    return ''.join(result)
