package edu.rpi.aris.ast;

import edu.rpi.aris.ast.Expression.*;
import java.util.*;

public interface ASTVisitor<T> {
    public T visitExpression(Expression e);
    public T visitBottomExpression(BottomExpression e);
    public T visitVarExpression(VarExpression e);
    public T visitApplyExpression(ApplyExpression e);
    public T visitUnaryExpression(UnaryExpression e);
    public T visitNotExpression(NotExpression e);
    public T visitBinaryExpression(BinaryExpression e);
    public T visitImplicationExpression(ImplicationExpression e);
    public T visitAddExpression(AddExpression e);
    public T visitMultExpression(MultExpression e);
    public T visitAssociativeBinopExpression(AssociativeBinopExpression e);
    public T visitAndExpression(AndExpression e);
    public T visitOrExpression(OrExpression e);
    public T visitBiconExpression(BiconExpression e);
    public T visitEquivExpression(EquivExpression e);
    public T visitQuantifierExpression(QuantifierExpression e);
    public T visitForallExpression(ForallExpression e);
    public T visitExistsExpression(ExistsExpression e);
}
