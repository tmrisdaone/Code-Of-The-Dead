public final class EventBus {
    private final EnumMap<EventType, List<Consumer<GameEvent>>> listeners;
    public void subscribe(EventType type, EventListener l);
    public void post(GameEvent e); // reuse event objects or use specific payloads
}
