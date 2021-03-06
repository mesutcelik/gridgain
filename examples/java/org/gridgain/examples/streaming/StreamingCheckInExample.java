/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.streaming;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.streamer.*;
import org.gridgain.grid.streamer.index.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * This streamer example is inspired by <a href="https://foursquare.com/">Foursquare</a>
 * project. It shows the usage of window indexes, and, particularly,
 * unique index, which allows to skip some duplicated events based on a certain
 * criteria.
 * <p>
 * In this example we have a number of places with 2D locations, and a number
 * of users who perform check-ins in random locations from time to time.
 * A check-in is a streamer event, which is processed through the pipeline
 * of 2 stages and added sequentially to two windows, both of which hold entries
 * within past 10 seconds (the rest of the entries are evicted).
 * <p>
 * First stage simply ensures the user does not check-in twice within the
 * 10 second time interval. This check is done by using a unique window hash index
 * with user name as a key. In case of a duplicate, an error is handled and
 * reported.
 * <p>
 * Second stage checks if a user has checked-in in one of the tracked places.
 * If that's the case, then an info entry is added to a second window. Again,
 * it is only valid for 10 seconds, and is evicted afterwards.
 * <p>
 * There is a separate timer task, which polls a second window index and displays
 * the users that have checked-in in the known places within the last 10 seconds.
 * <p>
 * Remote nodes should always be started with special configuration file which
 * enables P2P class loading: {@code 'ggstart.{sh|bat} examples/config/example-streaming.xml'}.
 * <p>
 * Alternatively you can run {@link StreamingNodeStartup} in another JVM which will start GridGain node
 * with {@code examples/config/example-streaming.xml} configuration.
 *
 * @author @java.author
 * @version @java.version
 */
public class StreamingCheckInExample {
    /** Streamer name. */
    private static final String STREAMER_NAME = "check-in";

    /**
     * Nearby distance. Locations with distance less than or equal
     * to this one are considered to be nearby.
     */
    private static final double NEARBY_DISTANCE = 5.0d;

    /** Random number generator. */
    private static final Random RAND = new Random();

    /** Total number of events to generate. */
    private static final int CNT = 60;

    /** User names. */
    private static final String[] USER_NAMES = {
        "Alice", "Bob", "Ann", "Joe", "Mary", "Peter", "Lisa", "Tom", "Kate", "Sam"
    };

    /** Places, for which to track user check-ins. */
    private static final Place[] TRACKED_PLACES = {
        new Place("Theatre", new Location(1.234, 2.567)),
        new Place("Bowling", new Location(10.111, 5.213)),
        new Place("Bar", new Location(15.199, 16.781)),
        new Place("Cinema", new Location(3.77, 20.239))
    };

    /** Max X coordinate. */
    private static final int MAX_X = 30;

    /** Max Y coordinate. */
    private static final int MAX_Y = 30;

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        Timer timer = new Timer("check-in-query-worker");

        // Start grid.
        final Grid g = GridGain.start("examples/config/example-streamer.xml");

        System.out.println();
        System.out.println(">>> Streaming check-in example started.");

        try {
            // Get the streamer.
            GridStreamer streamer = g.streamer(STREAMER_NAME);

            assert streamer != null;

            // Add a failure listener.
            streamer.addStreamerFailureListener(new GridStreamerFailureListener() {
                @Override public void onFailure(String stageName, Collection<Object> evts, Throwable err) {
                    System.err.println("Failure [stage=" + stageName + ", evts=" + evts + ", err=" + err.getMessage());
                }
            });

            // Periodically display users, who have checked-in in known places.
            scheduleQuery(streamer, timer);

            // Stream the check-in events.
            streamData(streamer);

            timer.cancel();

            // Reset all streamers on all nodes to make sure that
            // consecutive executions start from scratch.
            g.compute().run(new Runnable() {
                @Override public void run() {
                    GridStreamer streamer = g.streamer(STREAMER_NAME);

                    if (streamer == null)
                        System.err.println("Default streamer not found (is example-streamer.xml " +
                            "configuration used on all nodes?)");
                    else {
                        System.out.println("Clearing streamer data.");

                        streamer.reset();
                    }
                }
            }).get();
        }
        finally {
            GridGain.stop(true);
        }
    }

    /**
     * Schedules the query to periodically output the users, who have
     * checked-in in tracked places.
     *
     * @param streamer Streamer.
     * @param timer Timer.
     */
    private static void scheduleQuery(final GridStreamer streamer, Timer timer) {
        TimerTask task = new TimerTask() {
            @Override public void run() {
                try {
                    // Send reduce query to all streamers running on local and remote noes.
                    Map<String, Place> userPlaces = streamer.context().reduce(
                        // This closure will execute on remote nodes.
                        new GridClosure<GridStreamerContext, Map<String, Place>>() {
                            @Override public Map<String, Place> apply(
                                GridStreamerContext ctx) {
                                GridStreamerWindow<LocationInfo> win =
                                    ctx.window(DetectPlacesStage.class.getSimpleName());

                                assert win != null;

                                GridStreamerIndex<LocationInfo, String, Place> idxView = win.index();

                                Collection<GridStreamerIndexEntry<LocationInfo, String, Place>> entries =
                                    idxView.entries(0);

                                Map<String, Place> ret = new HashMap<>(entries.size());

                                for (GridStreamerIndexEntry<LocationInfo, String, Place> e : entries)
                                    ret.put(e.key(), e.value());

                                return ret;
                            }
                        },
                        new GridReducer<Map<String, Place>, Map<String, Place>>() {
                            private Map<String, Place> map;

                            @Override public boolean collect(@Nullable Map<String, Place> m) {
                                if (m == null)
                                    return false;

                                if (map != null)
                                    map.putAll(m);
                                else
                                    map = m;

                                return true;
                            }

                            @Override public Map<String, Place> reduce() {
                                return map;
                            }
                        }
                    );

                    StringBuilder sb = new StringBuilder("----------------\n");

                    for (Map.Entry<String, Place> userPlace : userPlaces.entrySet())
                        sb.append(String.format("%s is at the %s (%s)\n", userPlace.getKey(),
                            userPlace.getValue().name(), userPlace.getValue().location()));

                    sb.append("----------------\n");

                    System.out.print(sb.toString());
                }
                catch (GridException e) {
                    e.printStackTrace();
                }
            }
        };

        // Run task every 3 seconds.
        timer.schedule(task, 3000, 3000);
    }

    /**
     * Streams check-in events into the system.
     *
     * @param streamer Streamer.
     * @throws GridException If failed.
     */
    @SuppressWarnings("BusyWait")
    private static void streamData(GridStreamer streamer) throws GridException {
        try {
            for (int i = 0; i < CNT; i++) {
                CheckInEvent evt = new CheckInEvent(
                    USER_NAMES[ThreadLocalRandom.current().nextInt(USER_NAMES.length)],
                    new Location(
                        RAND.nextDouble() + RAND.nextInt(MAX_X - 1),
                        RAND.nextDouble() + RAND.nextInt(MAX_Y))
                );

                System.out.println(">>> Generating event: " + evt);

                streamer.addEvent(evt);

                Thread.sleep(1000);
            }
        }
        catch (InterruptedException ignored) {
            // No-op.
        }
    }

    /**
     * Entity class that represents a 2D location.
     */
    private static class Location {
        /** Check-in location on X axis (longitude). */
        private double x;

        /** Check-in location on Y axis (latitude). */
        private double y;

        /**
         * @param x X value.
         * @param y Y value.
         */
        private Location(double x, double y) {
            this.x = x;
            this.y = y;
        }

        /**
         * @return Check-in location on X axis (longitude).
         */
        public double x() {
            return x;
        }

        /**
         * @return Check-in location on Y axis (latitude).
         */
        public double y() {
            return y;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "Location [x=" + x + ", y=" + y + ']';
        }
    }

    /**
     * Entity class representing a place, where
     * users can check-in.
     */
    private static class Place {
        /** Place name. */
        private String name;

        /** Location. */
        private Location location;

        /**
         * @param name Name.
         * @param location Location.
         */
        private Place(String name, Location location) {
            this.name = name;
            this.location = location;
        }

        /**
         * @return Place name.
         */
        public String name() {
            return name;
        }

        /**
         * @return Location.
         */
        public Location location() {
            return location;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "Place [name=" + name + ", location=" + location + ']';
        }
    }

    /**
     * Check-in event.
     */
    private static class CheckInEvent {
        /** User name. */
        private String userName;

        /** User location. */
        private Location location;

        /**
         * @param userName User name.
         * @param location Location.
         */
        private CheckInEvent(String userName, Location location) {
            this.userName = userName;
            this.location = location;
        }

        /**
         * @return User name.
         */
        public String userName() {
            return userName;
        }

        /**
         * @return User location.
         */
        public Location location() {
            return location;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "CheckInEvent [userName=" + userName + ", location=" + location + ']';
        }
    }

    /**
     * Helper data structure for keeping information about
     * check-in location and a corresponding place if found.
     */
    private static class LocationInfo {
        /** User name. */
        private String userName;

        /** A detected check-in place. */
        private Place place;

        /**
         * @param userName User name.
         * @param place Place.
         */
        private LocationInfo(String userName, Place place) {
            this.userName = userName;
            this.place = place;
        }

        /**
         * @return User name.
         */
        public String userName() {
            return userName;
        }

        /**
         * @return A detected check-in place.
         */
        public Place place() {
            return place;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "LocationInfo [userName=" + userName + ", place=" + place + ']';
        }
    }

    /**
     * Check-in event processing stage that adds events window
     * with unique index to block repetitive check-ins.
     */
    @SuppressWarnings("PublicInnerClass")
    public static class AddToWindowStage implements GridStreamerStage<CheckInEvent> {
        /** {@inheritDoc} */
        @Override public String name() {
            return getClass().getSimpleName();
        }

        /** {@inheritDoc} */
        @Nullable @Override public Map<String, Collection<?>> run(
            GridStreamerContext ctx, Collection<CheckInEvent> evts) throws GridException {
            GridStreamerWindow<CheckInEvent> win = ctx.window(name());

            assert win != null;

            Collection<CheckInEvent> evts0 = new LinkedList<>();

            // Add events to window. Our unique index should reject
            // repetitive check-ins within a period of time, defined
            // by the window.
            for (CheckInEvent evt : evts) {
                try {
                    win.enqueue(evt);

                    evts0.add(evt);
                }
                catch (GridException e) {
                    if (e.getMessage().contains("Index unique key violation"))
                        System.err.println("Cannot check-in twice within the specified period of time [evt=" + evt + ']');
                    else
                        throw e;
                }
            }

            // Clear evicted events.
            win.pollEvictedAll();

            // Move to the next stage in pipeline, if there are valid events.
            if (!evts0.isEmpty())
                return Collections.<String, Collection<?>>singletonMap(ctx.nextStageName(), evts0);

            // Break the pipeline execution.
            return null;
        }
    }

    /**
     * Check-in event processing stage that detects the
     * check-in places.
     */
    private static class DetectPlacesStage implements GridStreamerStage<CheckInEvent> {
        /** {@inheritDoc} */
        @Override public String name() {
            return getClass().getSimpleName();
        }

        /** {@inheritDoc} */
        @Nullable @Override public Map<String, Collection<?>> run(GridStreamerContext ctx,
            Collection<CheckInEvent> evts) throws GridException {
            GridStreamerWindow<LocationInfo> win = ctx.window(name());

            assert win != null;

            for (CheckInEvent evt : evts) {
                for (Place place : TRACKED_PLACES) {
                    if (distance(evt.location(), place.location()) <= NEARBY_DISTANCE) {
                        win.enqueue(new LocationInfo(evt.userName(), place));

                        break;
                    }
                }
            }

            // Clear evicted location infos.
            win.pollEvictedAll();

            // Null means there are no more stages and
            // we should finish the pipeline.
            return null;
        }

        /**
         * Calculates the distance between 2 locations.
         *
         * @param loc1 First location.
         * @param loc2 Second location.
         * @return Distance between locations.
         */
        private double distance(Location loc1, Location loc2) {
            double xDiff = Math.abs(loc1.x() - loc2.x());
            double yDiff = Math.abs(loc1.y() - loc2.y());

            // Return a vector distance between the points.
            return Math.sqrt(xDiff * xDiff + yDiff * yDiff);
        }
    }

    /**
     * Index updater for check-in events.
     */
    private static class CheckInEventIndexUpdater implements GridStreamerIndexUpdater<CheckInEvent, String, Location> {
        /** {@inheritDoc} */
        @Nullable @Override public String indexKey(CheckInEvent evt) {
            return evt.userName(); // Index key is an event user name.
        }

        /** {@inheritDoc} */
        @Nullable @Override
        public Location initialValue(CheckInEvent evt, String key) {
            return evt.location(); // Index value is an event location.
        }

        /** {@inheritDoc} */
        @Nullable @Override public Location onAdded(
            GridStreamerIndexEntry<CheckInEvent, String, Location> entry,
            CheckInEvent evt) throws GridException {
            throw new AssertionError("onAdded() shouldn't be called on unique index.");
        }

        /** {@inheritDoc} */
        @Nullable @Override public Location onRemoved(
            GridStreamerIndexEntry<CheckInEvent, String, Location> entry,
            CheckInEvent evt) {
            return null;
        }
    }

    /**
     * Index updater for location info.
     */
    private static class PlacesIndexUpdater implements GridStreamerIndexUpdater<LocationInfo, String, Place> {
        /** {@inheritDoc} */
        @Nullable @Override public String indexKey(LocationInfo info) {
            return info.userName();
        }

        /** {@inheritDoc} */
        @Nullable @Override
        public Place initialValue(LocationInfo info, String key) {
            return info.place();
        }

        /** {@inheritDoc} */
        @Nullable @Override public Place onAdded(
            GridStreamerIndexEntry<LocationInfo, String, Place> entry,
            LocationInfo evt) throws GridException {
            throw new AssertionError("onAdded() shouldn't be called on unique index.");
        }

        /** {@inheritDoc} */
        @Nullable @Override public Place onRemoved(
            GridStreamerIndexEntry<LocationInfo, String, Place> entry,
            LocationInfo evt) {
            return null;
        }
    }
}
