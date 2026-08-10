package sh.zolt.jarproof.architecture;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreeScanner;

final class MethodComplexity {
    private int value = 1;
    private int maximumNesting;

    private MethodComplexity() {
    }

    static MethodComplexity measure(Tree methodBody) {
        MethodComplexity result = new MethodComplexity();
        new Scanner(result).scan(methodBody, 0);
        return result;
    }

    int value() {
        return value;
    }

    int maximumNesting() {
        return maximumNesting;
    }

    private static final class Scanner extends TreeScanner<Void, Integer> {
        private final MethodComplexity result;

        private Scanner(MethodComplexity result) {
            this.result = result;
        }

        @Override
        public Void visitIf(IfTree node, Integer depth) {
            return branch(depth, () -> super.visitIf(node, depth + 1));
        }

        @Override
        public Void visitForLoop(ForLoopTree node, Integer depth) {
            return branch(depth, () -> super.visitForLoop(node, depth + 1));
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree node, Integer depth) {
            return branch(depth, () -> super.visitEnhancedForLoop(node, depth + 1));
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree node, Integer depth) {
            return branch(depth, () -> super.visitWhileLoop(node, depth + 1));
        }

        @Override
        public Void visitDoWhileLoop(DoWhileLoopTree node, Integer depth) {
            return branch(depth, () -> super.visitDoWhileLoop(node, depth + 1));
        }

        @Override
        public Void visitSwitch(SwitchTree node, Integer depth) {
            return nested(depth, () -> super.visitSwitch(node, depth + 1));
        }

        @Override
        public Void visitSwitchExpression(SwitchExpressionTree node, Integer depth) {
            return nested(depth, () -> super.visitSwitchExpression(node, depth + 1));
        }

        @Override
        public Void visitCase(CaseTree node, Integer depth) {
            result.value++;
            return super.visitCase(node, depth);
        }

        @Override
        public Void visitTry(TryTree node, Integer depth) {
            return nested(depth, () -> super.visitTry(node, depth + 1));
        }

        @Override
        public Void visitCatch(CatchTree node, Integer depth) {
            result.value++;
            return super.visitCatch(node, depth);
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree node, Integer depth) {
            result.value++;
            return super.visitConditionalExpression(node, depth);
        }

        @Override
        public Void visitBinary(BinaryTree node, Integer depth) {
            if (node.getKind() == Tree.Kind.CONDITIONAL_AND || node.getKind() == Tree.Kind.CONDITIONAL_OR) {
                result.value++;
            }
            return super.visitBinary(node, depth);
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree node, Integer depth) {
            return null;
        }

        @Override
        public Void visitClass(ClassTree node, Integer depth) {
            return null;
        }

        private Void branch(int depth, java.util.function.Supplier<Void> scan) {
            result.value++;
            return nested(depth, scan);
        }

        private Void nested(int depth, java.util.function.Supplier<Void> scan) {
            result.maximumNesting = Math.max(result.maximumNesting, depth + 1);
            return scan.get();
        }
    }
}
