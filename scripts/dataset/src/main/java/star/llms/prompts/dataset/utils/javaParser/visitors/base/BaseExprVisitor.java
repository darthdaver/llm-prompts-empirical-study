package star.llms.prompts.dataset.utils.javaParser.visitors.base;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the nodes <E>
 * that extends {@link com.github.javaparser.ast.expr.Expression} found.
 *
 */
public abstract class BaseExprVisitor<E extends Expression> extends VoidVisitorAdapter<List<E>> {

    public List<E> visit(Statement statement) {
        List<E> collection = new java.util.ArrayList<>();
        if (statement.isAssertStmt()) {
            visit(statement.asAssertStmt(), collection);
        } else if (statement.isBlockStmt()) {
            visit(statement.asBlockStmt(), collection);
        } else if (statement.isBreakStmt()) {
            visit(statement.asBreakStmt(), collection);
        } else if (statement.isContinueStmt()) {
            visit(statement.asContinueStmt(), collection);
        } else if (statement.isDoStmt()) {
            visit(statement.asDoStmt(), collection);
        } else if (statement.isEmptyStmt()) {
            visit(statement.asEmptyStmt(), collection);
        } else if (statement.isExplicitConstructorInvocationStmt()) {
            visit(statement.asExplicitConstructorInvocationStmt(), collection);
        } else if (statement.isExpressionStmt()) {
            visit(statement.asExpressionStmt(), collection);
        } else if (statement.isForEachStmt()) {
            visit(statement.asForEachStmt(), collection);
        } else if (statement.isForStmt()) {
            visit(statement.asForStmt(), collection);
        } else if (statement.isIfStmt()) {
            visit(statement.asIfStmt(), collection);
        } else if (statement.isLabeledStmt()) {
            visit(statement.asLabeledStmt(), collection);
        } else if (statement.isLocalClassDeclarationStmt()) {
            visit(statement.asLocalClassDeclarationStmt(), collection);
        } else if (statement.isLocalRecordDeclarationStmt()) {
            visit(statement.asLocalRecordDeclarationStmt(), collection);
        } else if (statement.isReturnStmt()) {
            visit(statement.asReturnStmt(), collection);
        } else if (statement.isSwitchStmt()) {
            visit(statement.asSwitchStmt(), collection);
        } else if (statement.isSynchronizedStmt()) {
            visit(statement.asSynchronizedStmt(), collection);
        } else if (statement.isThrowStmt()) {
            visit(statement.asThrowStmt(), collection);
        } else if (statement.isTryStmt()) {
            visit(statement.asTryStmt(), collection);
        } else if (statement.isUnparsableStmt()) {
            visit(statement.asUnparsableStmt(), collection);
        } else if (statement.isWhileStmt()) {
            visit(statement.asWhileStmt(), collection);
        } else if (statement.isYieldStmt()) {
            visit(statement.asYieldStmt(), collection);
        }
        return collection;
    }

    public List<E> visit(MethodDeclaration methodDeclaration) {
        List<E> collection = new java.util.ArrayList<>();
        visit(methodDeclaration, collection);
        return collection;
    }

    // Generic method to add the statement to the collection
    protected void addToCollection(E stmt, List<E> collection) {
        collection.add(stmt);
    }
}