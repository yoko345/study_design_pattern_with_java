package chapter22.command;

import java.util.ArrayDeque;
import java.util.Deque;

public class MacroCommand implements Command {
    // 命令の列
    private Deque<Command> commands = new ArrayDeque<>();

    // 実行
    @Override
    public void execute() {
        for (Command command : commands) {
            command.execute();
        }
    }

    // 追加
    public void append(Command command) {
        if (command == this) {
            throw new IllegalArgumentException("infinite loop caused by append.");
        }

        commands.addLast(command);
    }

    // 元に戻す
    public void undo() {
        if (!commands.isEmpty()) {
            commands.removeLast();
        }
    }

    // 全命令削除
    public void clear() {
        commands.clear();
    }
}
