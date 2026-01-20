package com.hytown.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Event bus for HyTown events.
 * Allows other plugins to register listeners for town-related events.
 *
 * <p>Usage example from another plugin:
 * <pre>{@code
 * // Get the event bus from HyTown API
 * TownEventBus eventBus = hyTownPlugin.getEventBus();
 *
 * // Register a listener for town rename events
 * eventBus.on(TownRenameEvent.class, event -> {
 *     String oldName = event.getOldName();
 *     String newName = event.getNewName();
 *     // Update your plugin's references
 * });
 *
 * // Register a listener for town delete events
 * eventBus.on(TownDeleteEvent.class, event -> {
 *     // Clean up your plugin's data
 * });
 * }</pre>
 */
public class TownEventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * Register a listener for a specific event type.
     *
     * @param eventClass The event class to listen for
     * @param listener The listener callback
     * @param <T> The event type
     */
    @SuppressWarnings("unchecked")
    public <T> void on(Class<T> eventClass, Consumer<T> listener) {
        listeners.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(listener);
    }

    /**
     * Unregister a listener for a specific event type.
     *
     * @param eventClass The event class
     * @param listener The listener to remove
     * @param <T> The event type
     */
    public <T> void off(Class<T> eventClass, Consumer<T> listener) {
        List<Consumer<?>> eventListeners = listeners.get(eventClass);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    /**
     * Fire an event, notifying all registered listeners.
     *
     * @param event The event to fire
     * @param <T> The event type
     */
    @SuppressWarnings("unchecked")
    public <T> void fire(T event) {
        List<Consumer<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (Consumer<?> listener : eventListeners) {
                try {
                    ((Consumer<T>) listener).accept(event);
                } catch (Exception e) {
                    System.err.println("[TownEventBus] Error in event listener: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Clear all listeners for a specific event type.
     *
     * @param eventClass The event class
     */
    public void clearListeners(Class<?> eventClass) {
        listeners.remove(eventClass);
    }

    /**
     * Clear all listeners for all events.
     */
    public void clearAllListeners() {
        listeners.clear();
    }
}
