package org.checkerframework.checker.dividebyzero;

import org.checkerframework.dataflow.expression.JavaExpression;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFAnalysis;
import org.checkerframework.dataflow.cfg.node.*;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.analysis.RegularTransferResult;
import org.checkerframework.dataflow.analysis.ConditionalTransferResult;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;

import javax.lang.model.element.AnnotationMirror;
import java.lang.annotation.Annotation;

import java.util.Set;

import org.checkerframework.checker.dividebyzero.qual.*;

public class DivByZeroTransfer extends CFTransfer {

    enum Comparison {
        /** == */ EQ,
        /** != */ NE,
        /** <  */ LT,
        /** <= */ LE,
        /** >  */ GT,
        /** >= */ GE
    }

    enum BinaryOperator {
        /** + */ PLUS,
        /** - */ MINUS,
        /** * */ TIMES,
        /** / */ DIVIDE,
        /** % */ MOD
    }

    // ========================================================================
    // Transfer functions to implement

    /**
     * Assuming that a simple comparison (lhs `op` rhs) returns true, this
     * function should refine what we know about the left-hand side (lhs). (The
     * input value "lhs" is always a legal return value, but not a very useful
     * one.)
     *
     * <p>For example, given the code
     * <pre>
     * if (y != 0) { x = 1 / y; }
     * </pre>
     * the comparison "y != 0" causes us to learn the fact that "y is not zero"
     * inside the body of the if-statement. This function would be called with
     * "NE", "top", and "zero", and should return "not zero" (or the appropriate
     * result for your lattice).
     *
     * <p>Note that the returned value should always be lower in the lattice
     * than the given point for lhs. The "glb" helper function below will
     * probably be useful here.
     *
     * @param operator   a comparison operator
     * @param lhs        the lattice point for the left-hand side of the comparison expression
     * @param rhs        the lattice point for the right-hand side of the comparison expression
     * @return a refined type for lhs
     */
    private AnnotationMirror refineLhsOfComparison(
            Comparison operator,
            AnnotationMirror lhs,
            AnnotationMirror rhs) {
        // TODO
        AnnotationMirror top = top();
        AnnotationMirror nonzero = reflect(NonZero.class);
        AnnotationMirror positive = reflect(Positive.class);
        AnnotationMirror negative = reflect(Negative.class);
        AnnotationMirror zero = reflect(Zero.class);
        AnnotationMirror bottom = bottom();

        // bottom always prevails
        if (equal(lhs, bottom) || equal(rhs, bottom)) {
            return glb(lhs, bottom);
        }
        
        // in the following cases, we know that lhs or rhs is not bottom
        if (operator == Comparison.EQ) {
            return glb(lhs, rhs);
        } else if (operator == Comparison.NE) {
            // if ths is zero, then we can infer that it must be nonzero
            if (equal(rhs, zero)) {
                return glb(lhs, nonzero);
            }         
            // else we don't know, so it would be top
        } else if (operator == Comparison.LT) {
            // if < than zero or < negative, then we know that it must be negative
            if (equal(rhs, zero) || equal(rhs, negative)) {
                return glb(lhs, negative);
            } 
            // else we don't know, so it would be top
        } else if (operator == Comparison.LE) {
            // if <= negative, then it must be negative
            if (equal(rhs, negative)) {
                return glb(lhs, negative);
            } 
            // else we don't know, so it would be top
            // e.g. <= positive, can be positive, zero, negative, nonzero
            // similar examples apply to other cases
        } else if (operator == Comparison.GT) {
            // if > 0 or > positive, then it must be positive
            if (equal(rhs, positive) || equal(rhs, zero)) {
                return glb(lhs, positive);
            } 
            // else we don't know, so it would be top
        } else if (operator == Comparison.GE) {
            // if >= positive, then it must be positive
            if (equal(rhs, positive)) {
                return glb(lhs, positive);
            } 
            // else we don't know, so it would be top
        }

        return glb(lhs, top); // this is just top
    }

    /**
     * For an arithmetic expression (lhs `op` rhs), compute the point in the
     * lattice for the result of evaluating the expression. ("Top" is always a
     * legal return value, but not a very useful one.)
     *
     * <p>For example,
     * <pre>x = 1 + 0</pre>
     * should cause us to conclude that "x is not zero".
     *
     * @param operator   a binary operator
     * @param lhs        the lattice point for the left-hand side of the expression
     * @param rhs        the lattice point for the right-hand side of the expression
     * @return the lattice point for the result of the expression
     */
    private AnnotationMirror arithmeticTransfer(
            BinaryOperator operator,
            AnnotationMirror lhs,
            AnnotationMirror rhs) {
        // TODO
        AnnotationMirror top = top();
        AnnotationMirror nonzero = reflect(NonZero.class);
        AnnotationMirror positive = reflect(Positive.class);
        AnnotationMirror negative = reflect(Negative.class);
        AnnotationMirror zero = reflect(Zero.class);
        AnnotationMirror bottom = bottom();
        
        // need to cover all cases of transfer functions
        if (operator == BinaryOperator.PLUS) {
            // bottom prevails on others
            if (equal(lhs, bottom) || equal(rhs, bottom)) {
                return bottom;
            } else if (equal(lhs, top) || equal(rhs, top)) {
                // top with anything non bottom -> top
                return top;
            } else if (equal(lhs, nonzero) || equal(rhs, nonzero)) {
                // top and bottom excluded, anything plus nonzero is nonzero
                return nonzero;
            } else if (equal(rhs, zero)) {
                // if rhs is zero, then result will be whatever lhs is positive, negative
                return lhs;
            } else if (equal(lhs, zero)) {
                // if lhs is zero, then result will be whatever rhs is positive, negative
                return rhs;
            }
        } else if (operator == BinaryOperator.MINUS) {
            // bottom prevails on others
            if (equal(lhs, bottom) || equal(rhs, bottom)) {
                return bottom;
            } else if (equal(lhs, top) || equal(rhs, top)) {
                // top with anything non bottom -> top
                return top;
            } else if (equal(lhs, nonzero) || equal(rhs, nonzero)) {
                // top and bottom excluded, anything minus nonzero is nonzero
                return nonzero;
            } else if (equal(lhs, zero)) {
                // zero on lhs, rhs is its negation 
                if (equal(rhs, positive)) {
                    return negative;
                } else if (equal(rhs, negative)) {
                    return positive;
                } else if (equal(rhs, zero)) {
                    return zero;
                }
            } else if (equal(rhs, zero)) {
                // zero on rhs, just return lhs
                return lhs;
            }

        } else if (operator == BinaryOperator.TIMES) {
            // bottom prevails on others
            if (equal(rhs, bottom) || equal(lhs, bottom)) {
                return bottom;
            } else if (equal(lhs, zero) || equal(rhs, bottom)) {
                // excluding above, anything * zero is zero
                return zero;
            } else if (equal(lhs, top) || equal(rhs, top)) {
                // excluding above, anything * top is top
                return top;
            } else if (equal(lhs, nonzero) || equal(rhs, nonzero)) {
                // excluding above, anything * nonzero is nonzero
                return nonzero;
            } else if (equal(lhs, rhs) && equal(lhs, positive)) {
                // excluding above, pos*pos = pos
                return positive;
            } else if (equal(lhs, rhs) && equal(lhs, negative)) {
                // excluding above, neg*neg = pos
                return positive;
            } else if (!equal(rhs, lhs) && (equal(lhs, negative) || equal(rhs, negative))) {
                // excluding above, pos*neg = neg
                return negative;
            }
        } else if (operator == BinaryOperator.DIVIDE || operator == BinaryOperator.MOD) {
            if (equal(lhs, bottom) || equal(rhs, bottom) || equal(rhs, zero)) {
                // if bottom on lhs or rhs, or zero on rhs res is bottom
                return bottom;
            } else if (equal(lhs, zero) && !equal(rhs, top) ) {
                // excluding the first case, then if lhs is zero res is zero
                return zero;
            } // else is all top
        }

        return top;
    }

    // ========================================================================
    // Useful helpers

    /** Get the top of the lattice */
    private AnnotationMirror top() {
        return analysis.getTypeFactory().getQualifierHierarchy().getTopAnnotations().iterator().next();
    }

    /** Get the bottom of the lattice */
    private AnnotationMirror bottom() {
        return analysis.getTypeFactory().getQualifierHierarchy().getBottomAnnotations().iterator().next();
    }

    /** Compute the least-upper-bound of two points in the lattice */
    private AnnotationMirror lub(AnnotationMirror x, AnnotationMirror y) {
        return analysis.getTypeFactory().getQualifierHierarchy().leastUpperBound(x, y);
    }

    /** Compute the greatest-lower-bound of two points in the lattice */
    private AnnotationMirror glb(AnnotationMirror x, AnnotationMirror y) {
        return analysis.getTypeFactory().getQualifierHierarchy().greatestLowerBound(x, y);
    }

    /** Convert a "Class" object (e.g. "Top.class") to a point in the lattice */
    private AnnotationMirror reflect(Class<? extends Annotation> qualifier) {
        return AnnotationBuilder.fromClass(
            analysis.getTypeFactory().getProcessingEnv().getElementUtils(),
            qualifier);
    }

    /** Determine whether two AnnotationMirrors are the same point in the lattice */
    private boolean equal(AnnotationMirror x, AnnotationMirror y) {
        return AnnotationUtils.areSame(x, y);
    }

    /** `x op y` == `y flip(op) x` */
    private Comparison flip(Comparison op) {
        switch (op) {
            case EQ: return Comparison.EQ;
            case NE: return Comparison.NE;
            case LT: return Comparison.GT;
            case LE: return Comparison.GE;
            case GT: return Comparison.LT;
            case GE: return Comparison.LE;
            default: throw new IllegalArgumentException(op.toString());
        }
    }

    /** `x op y` == `!(x negate(op) y)` */
    private Comparison negate(Comparison op) {
        switch (op) {
            case EQ: return Comparison.NE;
            case NE: return Comparison.EQ;
            case LT: return Comparison.GE;
            case LE: return Comparison.GT;
            case GT: return Comparison.LE;
            case GE: return Comparison.LT;
            default: throw new IllegalArgumentException(op.toString());
        }
    }

    // ========================================================================
    // Checker Framework plumbing

    public DivByZeroTransfer(CFAnalysis analysis) {
        super(analysis);
    }

    private TransferResult<CFValue, CFStore> implementComparison(Comparison op, BinaryOperationNode n, TransferResult<CFValue, CFStore> out) {
        QualifierHierarchy hierarchy = analysis.getTypeFactory().getQualifierHierarchy();
        AnnotationMirror l = findAnnotation(analysis.getValue(n.getLeftOperand()).getAnnotations(), hierarchy);
        AnnotationMirror r = findAnnotation(analysis.getValue(n.getRightOperand()).getAnnotations(), hierarchy);

        if (l == null || r == null) {
            // this can happen for generic types
            return out;
        }

        CFStore thenStore = out.getThenStore().copy();
        CFStore elseStore = out.getElseStore().copy();

        thenStore.insertValue(
                JavaExpression.fromNode(n.getLeftOperand()),
            refineLhsOfComparison(op, l, r));

        thenStore.insertValue(
            JavaExpression.fromNode(n.getRightOperand()),
            refineLhsOfComparison(flip(op), r, l));

        elseStore.insertValue(
            JavaExpression.fromNode(n.getLeftOperand()),
            refineLhsOfComparison(negate(op), l, r));

        elseStore.insertValue(
            JavaExpression.fromNode(n.getRightOperand()),
            refineLhsOfComparison(flip(negate(op)), r, l));

        return new ConditionalTransferResult<>(out.getResultValue(), thenStore, elseStore);
    }

    private TransferResult<CFValue, CFStore> implementOperator(BinaryOperator op, BinaryOperationNode n, TransferResult<CFValue, CFStore> out) {
        QualifierHierarchy hierarchy = analysis.getTypeFactory().getQualifierHierarchy();
        AnnotationMirror l = findAnnotation(analysis.getValue(n.getLeftOperand()).getAnnotations(), hierarchy);
        AnnotationMirror r = findAnnotation(analysis.getValue(n.getRightOperand()).getAnnotations(), hierarchy);

        if (l == null || r == null) {
            // this can happen for generic types
            return out;
        }

        AnnotationMirror res = arithmeticTransfer(op, l, r);
        CFValue newResultValue = analysis.createSingleAnnotationValue(res, out.getResultValue().getUnderlyingType());
        return new RegularTransferResult<>(newResultValue, out.getRegularStore());
    }

    @Override
    public TransferResult<CFValue, CFStore> visitEqualTo(EqualToNode n, TransferInput<CFValue, CFStore> p) {
        return implementComparison(Comparison.EQ, n, super.visitEqualTo(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNotEqual(NotEqualNode n, TransferInput<CFValue, CFStore> p) {
        return implementComparison(Comparison.NE, n, super.visitNotEqual(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitGreaterThan(GreaterThanNode n, TransferInput<CFValue, CFStore> p) {
        return implementComparison(Comparison.GT, n, super.visitGreaterThan(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitGreaterThanOrEqual(GreaterThanOrEqualNode n, TransferInput<CFValue, CFStore> p) {
        return implementComparison(Comparison.GE, n, super.visitGreaterThanOrEqual(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitLessThan(LessThanNode n, TransferInput<CFValue, CFStore> p) {
        return implementComparison(Comparison.LT, n, super.visitLessThan(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitLessThanOrEqual(LessThanOrEqualNode n, TransferInput<CFValue, CFStore> p) {
        return implementComparison(Comparison.LE, n, super.visitLessThanOrEqual(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitIntegerDivision(IntegerDivisionNode n, TransferInput<CFValue, CFStore> p) {
        return implementOperator(BinaryOperator.DIVIDE, n, super.visitIntegerDivision(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitIntegerRemainder(IntegerRemainderNode n, TransferInput<CFValue, CFStore> p) {
        return implementOperator(BinaryOperator.MOD, n, super.visitIntegerRemainder(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitFloatingDivision(FloatingDivisionNode n, TransferInput<CFValue, CFStore> p) {
        return implementOperator(BinaryOperator.DIVIDE, n, super.visitFloatingDivision(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitFloatingRemainder(FloatingRemainderNode n, TransferInput<CFValue, CFStore> p) {
        return implementOperator(BinaryOperator.MOD, n, super.visitFloatingRemainder(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNumericalMultiplication(NumericalMultiplicationNode n, TransferInput<CFValue, CFStore> p) {
        return implementOperator(BinaryOperator.TIMES, n, super.visitNumericalMultiplication(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNumericalAddition(NumericalAdditionNode n, TransferInput<CFValue, CFStore> p) {
        return implementOperator(BinaryOperator.PLUS, n, super.visitNumericalAddition(n, p));
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNumericalSubtraction(NumericalSubtractionNode n, TransferInput<CFValue, CFStore> p) {
        return implementOperator(BinaryOperator.MINUS, n, super.visitNumericalSubtraction(n, p));
    }

    private static AnnotationMirror findAnnotation(
            Set<AnnotationMirror> set, QualifierHierarchy hierarchy) {
        if (set.size() == 0) {
            return null;
        }
        Set<? extends AnnotationMirror> tops = hierarchy.getTopAnnotations();
        return hierarchy.findAnnotationInSameHierarchy(set, tops.iterator().next());
    }

}
