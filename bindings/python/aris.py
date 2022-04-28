import ctypes
import struct
import os.path

deref_word = lambda addr, n: struct.unpack('<' + 'Q'*n, ctypes.string_at(addr, 8*n))

class Box_Expr(ctypes.Structure):
    _fields_ = [('ptr', ctypes.c_void_p)]
    def deref(self):
        #print ctypes.c_void_p(self.ptr), (ctypes.c_uint*16)(self.ptr)[0:16]
        #tmp = (ctypes.c_void_p*8)(*deref_word(self.ptr, 8))
        #print tmp[0:8]
        #print map(hex, tmp[0:8])
        return ctypes.cast(ctypes.c_void_p(self.ptr), ctypes.POINTER(Expr)).contents
        #return ctypes.cast(tmp, ctypes.POINTER(Expr)).contents
    def __repr__(self):
        #return 'Box(%r, %r)' % (hex(self.ptr), map(hex, deref_word(self.ptr, 4)))
        return 'Box(%r)' % (self.deref(),)
    def __str__(self):
        #print repr(self)
        return str(self.deref())

class String(ctypes.Structure):
    _fields_ = [('ptr', ctypes.c_void_p), ('capacity', ctypes.c_ulong), ('len', ctypes.c_ulong)]
    def __str__(self):
        #return 'String(%s, %d, %d, %r)' % (hex(self.ptr), self.len, self.capacity, map(hex, deref_word(ctypes.addressof(self), 20)))
        return ctypes.string_at(self.ptr, self.len)
        #return ctypes.string_at(self.ptr, deref_word(ctypes.addressof(self),3)[2])
    def __repr__(self):
        return repr(str(self))

def Vec(ty):
    class Vec_ty(ctypes.Structure):
        _fields_ = [('ptr', ctypes.c_void_p), ('capacity', ctypes.c_ulong), ('len', ctypes.c_ulong)]
        def __repr__(self):
            #print self.to_list(), str(self)
            return 'Vec<%r>(%r, %r, %d, %d, %r)' % (ty, hex(ctypes.addressof(self)), hex(self.ptr), self.capacity, self.len, map(hex, deref_word(ctypes.addressof(self), 8)))
        def __str__(self):
            return '[%s]' % (', '.join(str(x) for x in self.to_list()))
        def to_list(self):
            #print self.ptr, ty
            return ctypes.cast(ctypes.c_void_p(self.ptr), ctypes.POINTER(ty))[:self.len]
    return Vec_ty

class Expr(ctypes.Structure):
    def downcast(self):
        return getattr(self.body, {0: 'bottom', 1: 'var', 2: 'apply', 3: 'unop', 4: 'binop', 5: 'assoc_binop', 6: 'quantifier'}[self.tag])
    def __repr__(self):
        #return 'Expr(%r, %r)' % (hex(ctypes.addressof(self)), deref_word(ctypes.addressof(self), 4))
        return repr(self.downcast())
    def __str__(self):
        return str(self.downcast())

class Var_Body(ctypes.Structure):
    _fields_ = [('name', String)]
    def __repr__(self):
        return 'Var(%r)' % (self.name,)
    def __str__(self):
        return str(self.name)

class Apply_Body(ctypes.Structure):
    _fields_ = [('func', Box_Expr), ('args', Vec(Expr))]
    def __repr__(self):
        return 'Apply(%r, %r)' % (self.func, self.args.to_list())
    def __str__(self):
        return '%s(%s)' % (str(self.func), ', '.join(map(str, self.args.to_list())))

class Bottom_Body(ctypes.Structure):
    _fields_ = []
    def __repr__(self):
        return 'Bottom'
    def __str__(self):
        return '_|_'

class Unop_Body(ctypes.Structure):
    _fields_ = [('symbol', ctypes.c_uint), ('operand', Box_Expr)]
    def __repr__(self):
        #return 'Unop(0x%08x, %r, %r)' % (ctypes.addressof(self), self.operand.deref().downcast(), map(hex, deref_word(ctypes.addressof(self), 8)))
        return 'Unop(%d, %r)' % (self.symbol, self.operand)
    def __str__(self):
        return '%s%s' % ({0: '~'}[self.symbol], str(self.operand))

class Binop_Body(ctypes.Structure):
    _fields_ = [('symbol', ctypes.c_uint), ('left', Box_Expr), ('right', Box_Expr)]
    def symbolstr(self):
        return {0: '->', 1: '+', 2: '*'}[self.symbol]
    def __repr__(self):
        #print '%r %r' % (self.left.deref().downcast(), self.right.deref().downcast())
        #return 'Binop(0x%08x, %r)' % (ctypes.addressof(self), map(hex, deref_word(ctypes.addressof(self), 8)))
        return 'Binop(%r, %r, %r)' % (self.symbolstr(), self.left, self.right)
    def __str__(self):
        return '(%s %s %s)' % (self.left.deref().downcast(), self.symbolstr(), self.right.deref().downcast())

class AssocBinop_Body(ctypes.Structure):
    _fields_ = [('symbol', ctypes.c_uint), ('exprs', Vec(Expr))]
    def symbolstr(self):
        return {0: '&', 1: '|', 2: '<->'}[self.symbol]
    def __repr__(self):
        #return 'AssocBinop(%d, %r)' % (self.symbol, self.exprs)
        return 'AssocBinop(%r, %r)' % (self.symbolstr(), self.exprs.to_list())
    def __str__(self):
        return '(%s)' % ((' %s ' % (self.symbolstr(),)).join(map(str,self.exprs.to_list())),)
        #return repr(self)

class Quantifier_Body(ctypes.Structure):
    _fields_ = [
        ('symbol', ctypes.c_uint),
        ('pad', ctypes.c_uint),
        ('name', String),
        ('body', Box_Expr),
        ]
    def symbolstr(self):
        return {0: 'forall', 1: 'exists'}[self.symbol]
    def __repr__(self):
        return 'Quantifier(%r, %r, %r)' % (self.symbolstr(), self.name, self.body)
    def __str__(self):
        #print self.name
        return '%s %s, %s' % (self.symbolstr(), self.name, str(self.body))
    def get_body(self):
        return aris.aris_box_expr_deref(self.body)

class Expr_Body(ctypes.Union):
    _fields_ = [
        ('bottom', Bottom_Body),
        ('var', Var_Body),
        ('apply', Apply_Body),
        ('unop', Unop_Body),
        ('binop', Binop_Body),
        ('assoc_binop', AssocBinop_Body),
        ('quantifier', Quantifier_Body),
        ]
    def __repr__(self):
        return repr(self.downcast())

Expr._fields_ = [('tag', ctypes.c_uint), ('body', Expr_Body)]

class ExprVisitor:
    def fold_op(self, x, y): raise NotImplementedError('fold_op')
    def visit_Bottom(self, expr): return None
    def visit_Var(self, expr): return None
    def visit_Apply(self, expr): return self.fold_op(self.visit(expr.func), reduce(self.fold_op, map(self.visit, expr.args.to_list())))
    def visit_Unop(self, expr): return self.visit(expr.operand)
    def visit_Binop(self, expr): return self.fold_op(self.visit(expr.left), self.visit(expr.right))
    def visit_AssocBinop(self, expr): return reduce(self.fold_op, map(self.visit, expr.exprs.to_list()))
    def visit_Quantifier(self, expr): return self.visit(expr.body)
    def visit(self, expr):
        return {
            Expr: (lambda e: self.visit(e.downcast())),
            Box_Expr: (lambda e: self.visit(e.deref())),
            Bottom_Body: self.visit_Bottom,
            Var_Body: self.visit_Var,
            Apply_Body: self.visit_Apply,
            Unop_Body: self.visit_Unop,
            Binop_Body: self.visit_Binop,
            AssocBinop_Body: self.visit_AssocBinop,
            Quantifier_Body: self.visit_Quantifier,
        }[type(expr)](expr)

def freevars(expr):
    class FV(ExprVisitor):
        def fold_op(self, x, y): return (x or set()).union(y or set())
        def visit_Var(self, expr): return set([str(expr.name)])
        def visit_Quantifier(self, expr): return self.visit(expr.body).difference(set([str(expr.name)]))
    return FV().visit(expr)

scriptpath = os.path.abspath(os.path.dirname(__file__))
aris = ctypes.cdll[scriptpath+'../../target/release/libaris.so']

aris.aris_expr_parse.restype = ctypes.POINTER(Expr)

parse = lambda s: aris.aris_expr_parse(s)

#x = parse('exists x, AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA')
#x = parse('exists x, AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA -> (forall y, b+c) -> (AAAAAAAAAAAAAAAAAAAAA & BBBBBBBBBBBBBBBB & CCCCCCCCCCCCCCcc & DDDDDDDDDDDD & EEEEEEEEEEEEE)')
x = parse('exists x, AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA -> (forall y, b+c(x)) -> (AAAAAAAAAAAAAAAAAAAAA & ~BBBBBBBBBBBBBBBB & CCCCCCCCCCCCCCcc & DDDDDDDDDDDD & EEEEEEEEEEEEE & _|_)')

if __name__ == '__main__':
    print x.contents.downcast()
    print repr(x.contents)
    print freevars(x.contents)
    print freevars(parse('q & exists q, forall x, exists y, x+y+z').contents)

    #deref_word(0x41414141, 1)
