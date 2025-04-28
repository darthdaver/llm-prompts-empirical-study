package star.llms.prompts.dataset.data.enums;

import com.github.javaparser.ast.stmt.*;

public enum BlockStatementsType {
    DO(DoStmt.class),
    CATCH(TryStmt.class),
    IF_ELSE(IfStmt.class),
    ELSE(IfStmt.class),
    FINALLY(TryStmt.class),
    FOR_EACH(ForEachStmt.class),
    FOR(ForStmt.class),
    IF(IfStmt.class),
    LAMBDA(ExpressionStmt.class),
    METHOD_BODY(BlockStmt.class),
    SWITCH(SwitchStmt.class),
    TRY(TryStmt.class),
    WHILE(WhileStmt.class);

    private final Class<?> stmtClass;

    BlockStatementsType(Class<?> stmtClass) {
        this.stmtClass = stmtClass;
    }

    public Class<?> getJavaParserStmtClass() {
        return stmtClass;
    }

    public static boolean isBlockStatement(Statement statement) {
        if (
                statement.isBlockStmt() ||
                statement.isDoStmt() ||
                statement.isForEachStmt() ||
                statement.isForStmt() ||
                statement.isIfStmt() ||
                statement.isSwitchStmt() ||
                statement.isTryStmt() ||
                statement.isWhileStmt()
        ) {
            return true;
        } else {
            return false;
        }
    }
}
