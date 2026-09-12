package chapter16;

public interface Mediator {
    // Colleagueたちを生成する
    public abstract void createColleagues();

    // Colleagueの状態が変化したときに呼ばれる
    public abstract void colleaguesChanged();
}
