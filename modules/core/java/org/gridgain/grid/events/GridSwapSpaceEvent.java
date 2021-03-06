package org.gridgain.grid.events;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Grid swap space event.
 * <p>
 * Grid events are used for notification about what happens within the grid. Note that by
 * design GridGain keeps all events generated on the local node locally and it provides
 * APIs for performing a distributed queries across multiple nodes:
 * <ul>
 *      <li>
 *          {@link GridEvents#remoteQuery(GridPredicate, long)} -
 *          asynchronously querying events occurred on the nodes specified, including remote nodes.
 *      </li>
 *      <li>
 *          {@link GridEvents#localQuery(GridPredicate)} -
 *          querying only local events stored on this local node.
 *      </li>
 *      <li>
 *          {@link GridEvents#localListen(GridPredicate, int...)} -
 *          listening to local grid events (events from remote nodes not included).
 *      </li>
 * </ul>
 * User can also wait for events using method {@link GridEvents#waitForLocal(GridPredicate, int...)}.
 * <h1 class="header">Events and Performance</h1>
 * Note that by default all events in GridGain are enabled and therefore generated and stored
 * by whatever event storage SPI is configured. GridGain can and often does generate thousands events per seconds
 * under the load and therefore it creates a significant additional load on the system. If these events are
 * not needed by the application this load is unnecessary and leads to significant performance degradation.
 * <p>
 * It is <b>highly recommended</b> to enable only those events that your application logic requires
 * by using {@link GridConfiguration#getIncludeEventTypes()} method in GridGain configuration. Note that certain
 * events are required for GridGain's internal operations and such events will still be generated but not stored by
 * event storage SPI if they are disabled in GridGain configuration.
 *
 * @author @java.author
 * @version @java.version
 * @see GridEventType#EVT_SWAP_SPACE_DATA_READ
 * @see GridEventType#EVT_SWAP_SPACE_DATA_STORED
 * @see GridEventType#EVT_SWAP_SPACE_DATA_REMOVED
 * @see GridEventType#EVT_SWAP_SPACE_CLEARED
 * @see GridEventType#EVT_SWAP_SPACE_DATA_EVICTED
 */
public class GridSwapSpaceEvent extends GridEventAdapter {
    /** Swap space name. */
    private String space;

    /**
     * Creates swap space event.
     *
     * @param nodeId Node ID.
     * @param msg Optional message.
     * @param type Event type.
     * @param space Swap space name ({@code null} for default space).
     */
    public GridSwapSpaceEvent(UUID nodeId, String msg, int type, @Nullable String space) {
        super(nodeId, msg, type);

        this.space = space;
    }

    /**
     * Gets swap space name.
     *
     * @return Swap space name or {@code null} for default space.
     */
    @Nullable public String space() {
        return space;
    }

    /** {@inheritDoc} */
    @Override public String shortDisplay() {
        return name() + ": space=" + space;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridSwapSpaceEvent.class, this,
            "nodeId8", U.id8(nodeId()),
            "msg", message(),
            "type", name(),
            "tstamp", timestamp());
    }
}
