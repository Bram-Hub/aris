package edu.rpi.aris.ast;

import edu.rpi.aris.ast.Expression.*;
import java.util.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.*;

public class ASTConstructor extends ParseExpressionBaseVisitor<Expression> implements ParseExpressionVisitor<Expression> {
    public static Expression parse(String s) {
        ASTConstructor x = new ASTConstructor();
        CharStream cs = new ANTLRInputStream(s);
        Lexer lex = new ParseExpressionLexer(cs);
        ParseExpressionParser yacc = new ParseExpressionParser(new BufferedTokenStream(lex));
        yacc.setErrorHandler(new BailErrorStrategy());
        yacc.setBuildParseTree(true);
        try {
            try {
                return yacc.main().accept(x);
            } catch(ParseCancellationException pce) {
                //pce.printStackTrace();
                System.out.printf("PCE: %s\n", pce.toString());
                Throwable t = pce;
                while(t != null) {
                    System.out.printf("\t%s %s\n", t.toString(), t.getMessage());
                    if(t instanceof RecognitionException) {
                        throw (RecognitionException)t;

                    }
                    t = t.getCause();
                }
                return null;
            }
        } catch(RecognitionException re) {
            Token token = re.getOffendingToken();
            System.out.printf("\t\tUnexpected token '%s' at %d-%d, (Expected: %s)\n", token.getText(), token.getStartIndex(), token.getStopIndex()+1, re.getExpectedTokens().toString(ParseExpressionParser.VOCABULARY));
            return null;
        }

    }
    ArrayList<Expression> pred_args = null;
	//@Override public Expression visitMain(ParseExpressionParser.MainContext ctx) { return null; }
	@Override public Expression visitPredicate(ParseExpressionParser.PredicateContext ctx) {
        // System.out.printf("visitPredicate %d\n", ctx.getAltNumber());
        ApplyExpression pe = new ApplyExpression();
        VarExpression func = new VarExpression();
        func.name = ctx.VARIABLE().getText();
        pe.func = func;
        assert(pred_args == null); // this shouldn't be reentrantly invoked
        if(ctx.getAltNumber() == 2) {
            pred_args = new ArrayList();
            ctx.arg_list().accept(this);

            pe.args = pred_args;
            pred_args = null;
        }
        // System.out.printf("visitPredicate returning %s\n", pe);
        return pe;
    }
	@Override public Expression visitArg_list(ParseExpressionParser.Arg_listContext ctx) {
        // System.out.printf("visitArg_list %d\n", ctx.getAltNumber());
        visitChildren(ctx);
        ApplyExpression pe = new ApplyExpression();
        VarExpression func = new VarExpression();
        func.name = ctx.VARIABLE().getText();
        pe.func = func;
        pe.args = new ArrayList();
        pred_args.add(0, pe);
        return null;
    }
	@Override public Expression visitForallQuantifier(ParseExpressionParser.ForallQuantifierContext ctx) {
        return new ForallExpression();
    }
	@Override public Expression visitExistsQuantifier(ParseExpressionParser.ExistsQuantifierContext ctx) {
        return new ExistsExpression();
    }
	@Override public Expression visitQuantifier(ParseExpressionParser.QuantifierContext ctx) {
        return visitChildren(ctx);
    }
	@Override public Expression visitBinder(ParseExpressionParser.BinderContext ctx) {
        //visitChildren(ctx);
        QuantifierExpression qe = (QuantifierExpression)ctx.quantifier().accept(this);
        qe.boundvar = ctx.VARIABLE().getText();
        qe.body = ctx.paren_expr().accept(this);
        // System.out.printf("visitBinder returning %s\n", qe);
        return qe;
    }
	//@Override public Expression visitAndrepr(ParseExpressionParser.AndreprContext ctx) { return null; }
	@Override public Expression visitAndterm(ParseExpressionParser.AndtermContext ctx) {
        // System.out.printf("visitAndTerm %d\n", ctx.getAltNumber());
        AssociativeBinopExpression abe = ctx.getAltNumber() == 2 ?  new AndExpression() : (AndExpression)ctx.andterm().accept(this);
        abe.addOperand(ctx.paren_expr().accept(this));
        // System.out.printf("visitAndTerm returning %s\n", abe);
        return abe;
    }
	//@Override public Expression visitOrrepr(ParseExpressionParser.OrreprContext ctx) { return null; }
	@Override public Expression visitOrterm(ParseExpressionParser.OrtermContext ctx) {
        // System.out.printf("visitOrTerm %d\n", ctx.getAltNumber());
        AssociativeBinopExpression abe = ctx.getAltNumber() == 2 ? new OrExpression() : (OrExpression)ctx.orterm().accept(this);
        abe.addOperand(ctx.paren_expr().accept(this));
        // System.out.printf("visitOrTerm returning %s\n", abe);
        return abe;
    }
	//@Override public Expression visitBiconrepr(ParseExpressionParser.BiconreprContext ctx) { return null; }
	@Override public Expression visitBiconterm(ParseExpressionParser.BicontermContext ctx) {
        // System.out.printf("visitBiconTerm %d\n", ctx.getAltNumber());
        AssociativeBinopExpression abe = ctx.getAltNumber() == 2 ? new BiconExpression() : (BiconExpression)ctx.biconterm().accept(this);
        abe.addOperand(ctx.paren_expr().accept(this));
        // System.out.printf("visitBiconTerm returning %s\n", abe);
        return abe;
    }
	//@Override public Expression visitAssocterm(ParseExpressionParser.AssoctermContext ctx) { return null; }
	//@Override public Expression visitBinop(ParseExpressionParser.BinopContext ctx) { return null; }
	@Override public Expression visitBinopterm(ParseExpressionParser.BinoptermContext ctx) {
        BinaryExpression be = null;
        if(ctx.BINOP().getText().equals("->")) { be = new ImplicationExpression(); }
        if(ctx.BINOP().getText().equals("+")) { be = new AddExpression(); }
        if(ctx.BINOP().getText().equals("*")) { be = new MultExpression(); }
        assert(be != null);
        be.l = ctx.paren_expr(0).accept(this);
        be.r = ctx.paren_expr(1).accept(this);
        return be;
    }
	@Override public Expression visitNotterm(ParseExpressionParser.NottermContext ctx) {
        UnaryExpression ue = new NotExpression();
        ue.operand = ctx.paren_expr().accept(this);
        // System.out.printf("visitNotterm returning %s\n", ue);
        return ue;
    }
	@Override public Expression visitBottom(ParseExpressionParser.BottomContext ctx) {
        return new BottomExpression();
    }
	@Override public Expression visitParen_expr(ParseExpressionParser.Paren_exprContext ctx) {
        Expression e = visitChildren(ctx);
        // System.out.printf("visitParen_expr returning %s\n", e);
        return e;
    }
	@Override public Expression visitExpr(ParseExpressionParser.ExprContext ctx) {
        return visitChildren(ctx);
    }
    @Override public Expression visitErrorNode(ErrorNode e) { return null; }
    @Override protected Expression aggregateResult(Expression e1, Expression e2) {
        Expression e = super.aggregateResult(e1, e2);
        if(e == null && e1 != null) { e = e1; }
        if(e == null && e2 != null) { e = e2; }
        // System.out.printf("aggregateResult(%s, %s) -> %s\n", e1, e2, e);
        return e;
    }
}
