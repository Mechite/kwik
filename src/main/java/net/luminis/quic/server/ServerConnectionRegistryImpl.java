/*
 * Copyright © 2023 Peter Doornbosch
 *
 * This file is part of Kwik, an implementation of the QUIC protocol in Java.
 *
 * Kwik is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Kwik is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.luminis.quic.server;

import net.luminis.quic.log.Logger;
import net.luminis.tls.util.ByteUtils;

import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ServerConnectionRegistryImpl implements ServerConnectionRegistry {

    private final Logger log;
    private final Map<ConnectionSource, ServerConnectionProxy> currentConnections;

    ServerConnectionRegistryImpl(Logger log) {
        this.log = log;
        currentConnections = new ConcurrentHashMap<>();
    }

    @Override
    public void registerConnection(ServerConnectionProxy connection, byte[] connectionId) {
        currentConnections.put(new ConnectionSource(connectionId), connection);
    }

    @Override
    public void deregisterConnection(ServerConnectionProxy connection, byte[] connectionId) {
        boolean removed = currentConnections.remove(new ConnectionSource(connectionId), connection);
        if (! removed && currentConnections.containsKey(new ConnectionSource(connectionId))) {
            log.error("Connection " + connection + " not removed, because "
                    + currentConnections.get(new ConnectionSource(connectionId)) + " is registered for "
                    + ByteUtils.bytesToHex(connectionId));
        }
    }

    @Override
    public void registerAdditionalConnectionId(byte[] currentConnectionId, byte[] newConnectionId) {
        ServerConnectionProxy connection = currentConnections.get(new ConnectionSource(currentConnectionId));
        if (connection != null) {
            currentConnections.put(new ConnectionSource(newConnectionId), connection);
        }
        else {
            log.error("Cannot add additional cid to non-existing connection " + ByteUtils.bytesToHex(currentConnectionId));
        }
    }

    @Override
    public void deregisterConnectionId(byte[] connectionId) {
        currentConnections.remove(new ConnectionSource(connectionId));
    }

    Optional<ServerConnectionProxy> isExistingConnection(InetSocketAddress clientAddress, byte[] dcid) {
        return Optional.ofNullable(currentConnections.get(new ConnectionSource(dcid)));
    }

    void removeConnection(ServerConnectionImpl connection) {
        ServerConnectionProxy removed = null;
        for (byte[] connectionId: connection.getActiveConnectionIds()) {
            if (removed == null) {
                removed = currentConnections.remove(new ConnectionSource(connectionId));
                if (removed == null) {
                    log.error("Cannot remove connection with cid " + ByteUtils.bytesToHex(connectionId));
                }
            }
            else {
                if (removed != currentConnections.remove(new ConnectionSource(connectionId))) {
                    log.error("Removed connections for set of active cids are not identical");
                }
            }
        }
        currentConnections.remove(new ConnectionSource(connection.getOriginalDestinationConnectionId()));

        if (! removed.isClosed()) {
            log.error("Removed connection with dcid " + ByteUtils.bytesToHex(connection.getOriginalDestinationConnectionId()) + " that is not closed...");
        }
        removed.terminate();
    }


    /**
     * Logs the entire connection table. For debugging purposed only.
     */
    void logConnectionTable() {
        log.info("Connection table: \n" +
                currentConnections.entrySet().stream()
                        .sorted(new Comparator<Map.Entry<ConnectionSource, ServerConnectionProxy>>() {
                            @Override
                            public int compare(Map.Entry<ConnectionSource, ServerConnectionProxy> o1, Map.Entry<ConnectionSource, ServerConnectionProxy> o2) {
                                return o1.getValue().toString().compareTo(o2.getValue().toString());
                            }
                        })
                        .map(e -> e.getKey() + "->" + e.getValue())
                        .collect(Collectors.joining("\n")));

    }
}
