package chapter16;

public interface Colleague {
    // Mediatorを設定する
    public abstract void setMediator(Mediator mediator);

    // Mediatorから有効/無効が指示される
    public abstract void setColleagueEnabled(boolean enabled);
}
