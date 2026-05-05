class _Dummy:
    __slots__ = ()

    def __init__(self, *args, **kwargs):
        pass

    def __call__(self, *args, **kwargs):
        return []

    def __getattr__(self, name):
        return _Dummy()

    def __getitem__(self, key):
        return _Dummy()

    def __iter__(self):
        return iter([])

    def __repr__(self):
        return "<jsonpath stub>"


jsonpath = _Dummy
findall = lambda *a, **kw: []
