package chapter23.practice.turtle;

import chapter23.practice.language.Executor;
import chapter23.practice.language.ExecutorFactory;

/**
 * タートルの基本コマンド（go, left, right）に対応する Executor を生成するファクトリです。
 *
 * <p>生成した Executor は、対象の TurtleCanvas を操作します。
 */
public class TurtleExecutorFactory implements ExecutorFactory {
    /**
     * 操作対象の Canvas
     */
    private final TurtleCanvas canvas;

    /**
     * "go" を実行。タートルを 1 単位分進めます
     */
    private class GoExecutor implements Executor {
        /**
         * タートルを単位長さ分進めます。
         */
        @Override
        public void execute() {
            canvas.go(TurtleCanvas.UNIT_LENGTH);
        }
    }

    /**
     * "left" を実行。タートルを左に 90 度向けます
     */
    private class LeftExecutor implements Executor {
        /**
         * タートルの向きを左に回転させます。
         */
        @Override
        public void execute() {
            canvas.setRelativeDirection(TurtleCanvas.RELATIVE_DIRECTION_LEFT);
        }
    }

    /**
     * "right" を実行。タートルを右に 90 度向けます
     */
    private class RightExecutor implements Executor {
        /**
         * タートルの向きを右に回転させます。
         */
        @Override
        public void execute() {
            canvas.setRelativeDirection(TurtleCanvas.RELATIVE_DIRECTION_RIGHT);
        }
    }

    /**
     * 操作対象の Canvas を指定して生成します。
     *
     * @param canvas 操作対象の TurtleCanvas
     */
    public TurtleExecutorFactory(TurtleCanvas canvas) {
        this.canvas = canvas;
    }

    /**
     * 名前に対応するタートル操作の Executor を生成します。
     *
     * @param name 基本コマンドの名前（go, left, right）
     * @return 名前に対応する Executor
     * @throws IllegalArgumentException 未知の名前が指定された場合
     */
    @Override
    public Executor createExecutor(String name) {
        if (name.equals("go")) {
            return new GoExecutor();
        } else if (name.equals("left")) {
            return new LeftExecutor();
        } else if (name.equals("right")) {
            return new RightExecutor();
        } else {
            throw new IllegalArgumentException("Error: Unknown <primitive command>: \"" + name + "\"");
        }
    }
}
