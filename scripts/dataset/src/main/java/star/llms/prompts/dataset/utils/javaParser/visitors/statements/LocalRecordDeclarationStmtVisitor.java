package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt} found.
 *
 */
public class LocalRecordDeclarationStmtVisitor extends BaseStmtVisitor<LocalRecordDeclarationStmt> {

    /**
     * Visit a node and collects all the local record declaration statements found within is AST.
     *
     * @param localRecordDeclarationStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(LocalRecordDeclarationStmt localRecordDeclarationStmt, List<LocalRecordDeclarationStmt> collection) {
        super.visit(localRecordDeclarationStmt, collection);
        addToCollection(localRecordDeclarationStmt, collection);
    }
}
