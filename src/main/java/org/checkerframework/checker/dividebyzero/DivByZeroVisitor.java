package org.checkerframework.checker.dividebyzero;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;

import javax.lang.model.type.TypeKind;
import java.lang.annotation.Annotation;

import com.github.javaparser.ast.expr.Expression;
import com.sun.source.tree.*;

import java.util.Set;
import java.util.EnumSet;

import org.checkerframework.checker.dividebyzero.qual.*;

public class DivByZeroVisitor extends BaseTypeVisitor<DivByZeroAnnotatedTypeFactory> {

    /** Set of operators we care about */
    private static final Set<Tree.Kind> DIVISION_OPERATORS = EnumSet.of(
        /* x /  y */ Tree.Kind.DIVIDE,
        /* x /= y */ Tree.Kind.DIVIDE_ASSIGNMENT,
        /* x %  y */ Tree.Kind.REMAINDER,
        /* x %= y */ Tree.Kind.REMAINDER_ASSIGNMENT);

    /**
     * Determine whether to report an error at the given binary AST node.
     * The error text is defined in the messages.properties file.
     * @param node the AST node to inspect
     * @return true if an error should be reported, false otherwise
     */
    private boolean errorAt(BinaryTree node) {
        // A BinaryTree can represent any binary operator, including + or -.
        // TODO
        if (node == null) {
            return false;
        }

        Tree.Kind opKind = node.getKind();

        // if it is not / or %, we do not care
        if (!DIVISION_OPERATORS.contains(opKind)) {
            return false;
        }

        // It's / or %
        ExpressionTree leftOperand = node.getLeftOperand();
        ExpressionTree rightOperand = node.getRightOperand();

        // we only care about integer division
        if (!isInt(leftOperand) || !isInt(rightOperand)) {
            return false;
        }

        // if rightOperand is either zero or top, then possible division by zero
        return hasAnnotation(rightOperand, Zero.class) || hasAnnotation(rightOperand, Top.class);
    }

    /**
     * Determine whether to report an error at the given compound assignment
     * AST node. The error text is defined in the messages.properties file.
     * @param node the AST node to inspect
     * @return true if an error should be reported, false otherwise
     */
    private boolean errorAt(CompoundAssignmentTree node) {
        // A CompoundAssignmentTree represents any binary operator combined with an assignment,
        // such as "x += 10".
        // TODO
        if (node == null) {
            return false;
        }
        
        Tree.Kind opKind = node.getKind();
        // if it is not / or %, we do not care
        if (!DIVISION_OPERATORS.contains(opKind)) {
            return false;
        }

        // it is probably fine if left hand side is a long. int will be converted
        ExpressionTree rightHandSide = node.getExpression();
        if (!isInt(rightHandSide)) {
            return false;
        }

        // if righHandSide is either zero or top, then possible division by zero
        return hasAnnotation(rightHandSide, Zero.class) || hasAnnotation(rightHandSide, Top.class);
    }

    // ========================================================================
    // Useful helpers

    private static final Set<TypeKind> INT_TYPES = EnumSet.of(
        TypeKind.INT,
        TypeKind.LONG);

    private boolean isInt(Tree node) {
        return INT_TYPES.contains(atypeFactory.getAnnotatedType(node).getKind());
    }

    private boolean hasAnnotation(Tree node, Class<? extends Annotation> c) {
        return atypeFactory.getAnnotatedType(node).hasAnnotation(c);
    }

    // ========================================================================
    // Checker Framework plumbing

    public DivByZeroVisitor(BaseTypeChecker c) {
        super(c);
    }

    @Override
    public Void visitBinary(BinaryTree node, Void p) {
        if (isInt(node)) {
            if (errorAt(node)) {
                checker.reportError(node, "divide.by.zero");
            }
        }
        return super.visitBinary(node, p);
    }

    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        if (isInt(node.getExpression())) {
            if (errorAt(node)) {
                checker.reportError(node, "divide.by.zero");
            }
        }
        return super.visitCompoundAssignment(node, p);
    }

}
