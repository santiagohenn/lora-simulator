package structures;

import org.jetbrains.annotations.NotNull;

public class Event implements Comparable<Event> {

    private final long time;
    private final int fromID;
    private final int toID;
    private final boolean type; // 1: gain contact, 0: lost contact

    public Event(long time, int fromID, int toID, boolean type) {
        this.time = time;
        this.fromID = fromID;
        this.toID = toID;
        this.type = type;
    }

    public long getTime() {
        return time;
    }

    public int getFromID() {
        return fromID;
    }

    public int getToID() {
        return toID;
    }

    public boolean gainedContact() {
        return type;
    }

    @Override
    public String toString() {
        return time + "," + fromID + "," + toID;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Event objAsEvent) {
            return objAsEvent.getFromID() == this.fromID && objAsEvent.getToID() == this.toID;
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(@NotNull Event o) {
        return Long.compare(this.time, o.time);
    }

}
