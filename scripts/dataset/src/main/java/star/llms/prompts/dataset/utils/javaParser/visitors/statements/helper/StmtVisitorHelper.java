package star.llms.prompts.dataset.utils.javaParser.visitors.statements.helper;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.*;
import star.llms.prompts.dataset.utils.javaParser.visitors.expressions.MethodCallExprVisitor;
import star.llms.prompts.dataset.utils.javaParser.visitors.expressions.ObjectCreationExprVisitor;
import star.llms.prompts.dataset.utils.javaParser.visitors.statements.*;

import java.util.List;
import java.util.Optional;

public class StmtVisitorHelper {
    public static Optional<Statement> getLastStatementOccurrence(Statement statement, Class<? extends Statement> type) {
        List<Statement> stmts;
        if (type.equals(AssertStmt.class)) {
            AssertStmtVisitor assertStmtCollector = new AssertStmtVisitor();
            stmts = assertStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(BlockStmt.class)) {
            BlockStmtVisitor blockStmtCollector = new BlockStmtVisitor();
            stmts = blockStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(BreakStmt.class)) {
            BreakStmtVisitor breakStmtCollector = new BreakStmtVisitor();
            stmts = breakStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(ContinueStmt.class)) {
            ContinueStmtVisitor continueStmtCollector = new ContinueStmtVisitor();
            stmts = continueStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(DoStmt.class)) {
            DoStmtVisitor doStmtCollector = new DoStmtVisitor();
            stmts = doStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(EmptyStmt.class)) {
            EmptyStmtVisitor emptyStmtCollector = new EmptyStmtVisitor();
            stmts = emptyStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(ExplicitConstructorInvocationStmt.class)) {
            ExplicitConstructorInvocationStmtVisitor explicitConstructorInvocationStmtCollector = new ExplicitConstructorInvocationStmtVisitor();
            stmts = explicitConstructorInvocationStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(ExpressionStmt.class)) {
            ExpressionStmtVisitor expressionStmtCollector = new ExpressionStmtVisitor();
            stmts = expressionStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(ForEachStmt.class)) {
            ForEachStmtVisitor forEachStmtCollector = new ForEachStmtVisitor();
            stmts = forEachStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(ForStmt.class)) {
            ForStmtVisitor forStmtCollector = new ForStmtVisitor();
            stmts = forStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(IfStmt.class)) {
            IfStmtVisitor ifStmtCollector = new IfStmtVisitor();
            stmts = ifStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(LabeledStmt.class)) {
            LabeledStmtVisitor labeledStmtCollector = new LabeledStmtVisitor();
            stmts = labeledStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(LocalClassDeclarationStmt.class)) {
            LocalClassDeclarationStmtVisitor localClassDeclarationStmtCollector = new LocalClassDeclarationStmtVisitor();
            stmts = localClassDeclarationStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(LocalRecordDeclarationStmt.class)) {
            LocalRecordDeclarationStmtVisitor localRecordDeclarationStmtCollector = new LocalRecordDeclarationStmtVisitor();
            stmts = localRecordDeclarationStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(ReturnStmt.class)) {
            ReturnStmtVisitor returnStmtCollector = new ReturnStmtVisitor();
            stmts = returnStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(SwitchStmt.class)) {
            SwitchStmtVisitor switchStmtCollector = new SwitchStmtVisitor();
            stmts = switchStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(SynchronizedStmt.class)) {
            SynchronizedStmtVisitor synchronizedStmtCollector = new SynchronizedStmtVisitor();
            stmts = synchronizedStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(ThrowStmt.class)) {
            ThrowStmtVisitor throwStmtCollector = new ThrowStmtVisitor();
            stmts = throwStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(TryStmt.class)) {
            TryStmtVisitor tryStmtCollector = new TryStmtVisitor();
            stmts = tryStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(UnparsableStmt.class)) {
            UnparsableStmtVisitor unparsableStmtCollector = new UnparsableStmtVisitor();
            stmts = unparsableStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(WhileStmt.class)) {
            WhileStmtVisitor whileStmtCollector = new WhileStmtVisitor();
            stmts = whileStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else if (type.equals(YieldStmt.class)) {
            YieldStmtVisitor yieldStmtCollector = new YieldStmtVisitor();
            stmts = yieldStmtCollector.visit(statement).stream().map(Statement.class::cast).toList();
        } else {
            throw new IllegalStateException("Unexpected block statement type");
        }
        if (stmts.size() > 0) {
            return Optional.of(stmts.get(stmts.size() - 1));
        }
        return Optional.empty();
    }

    public static Optional<Expression> getLastExprInStmt(Statement statement, Class<?> type) {
        List<Expression> exprs;
        if (type.equals(MethodCallExpr.class)) {
            MethodCallExprVisitor methodCallExprVisitor = new MethodCallExprVisitor();
            exprs = methodCallExprVisitor.visit(statement).stream().map(Expression.class::cast).toList();
        } else if (type.equals(ObjectCreationExpr.class)) {
            ObjectCreationExprVisitor objectCreationExprVisitor = new ObjectCreationExprVisitor();
            exprs = objectCreationExprVisitor.visit(statement).stream().map(Expression.class::cast).toList();
        } else {
            throw new IllegalStateException("Unexpected expression type");
        }
        if (exprs.size() > 0) {
            return Optional.of(exprs.get(exprs.size() - 1));
        }
        return Optional.empty();
    }

    public static List<ExpressionStmt> getAllExpressionStmts(Statement statement) {
        ExpressionStmtVisitor expressionStmtCollector = new ExpressionStmtVisitor();
        return expressionStmtCollector.visit(statement);
    }
}
