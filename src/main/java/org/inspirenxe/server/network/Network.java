/**
 * This file is part of Pulse, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2014 InspireNXE <http://inspirenxe.org/>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.inspirenxe.server.network;

import java.net.InetSocketAddress;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.flowpowered.commons.ticking.TickingElement;
import com.flowpowered.networking.session.BasicSession;
import com.flowpowered.networking.session.Session;
import com.flowpowered.networking.util.AnnotatedMessageHandler;
import com.flowpowered.networking.util.AnnotatedMessageHandler.Handle;
import org.inspirenxe.server.Game;
import org.inspirenxe.server.network.ChannelMessage.Channel;
import org.inspirenxe.server.network.message.handshake.HandshakeMessage;
import org.inspirenxe.server.network.message.login.LoginStartMessage;
import org.inspirenxe.server.network.protocol.LoginProtocol;

public class Network extends TickingElement {
    private static final int TPS = 20;
    private final Game game;
    private final GameNetworkServer server;
    private final AnnotatedMessageHandler handler = new AnnotatedMessageHandler(this);
    private final Map<Channel, ConcurrentLinkedQueue<ChannelMessage>> messageQueue = new EnumMap<>(Channel.class);

    public Network(Game game) {
        super("network", TPS);
        this.game = game;
        server = new GameNetworkServer(game);
        messageQueue.put(Channel.NETWORK, new ConcurrentLinkedQueue<ChannelMessage>());
    }

    @Override
    public void onStart() {
        game.getLogger().info("Starting network");
        final InetSocketAddress address = new InetSocketAddress(game.getConfiguration().getAddress(), game.getConfiguration().getPort());
        server.bind(address);
        game.getLogger().info("Listening on " + address);
    }

    @Override
    public void onTick(long l) {
        final Iterator<ChannelMessage> messages = getChannel(Channel.NETWORK);
        while (messages.hasNext()) {
            handler.handle(messages.next());
            messages.remove();
        }

        for (Session session : server.getSessions()) {
            final BasicSession basic = (BasicSession) session;
            if (!basic.getChannel().isOpen()) {
                continue;
            }
            if (!basic.getChannel().config().isAutoRead()) {
                basic.getChannel().read();
            }
        }
    }

    @Override
    public void onStop() {
        game.getLogger().info("Stopping network");
        server.shutdown();
    }

    /**
     * Gets the {@link java.util.Iterator} storing the messages for the {@link ChannelMessage.Channel}
     *
     * @param c See {@link ChannelMessage}
     * @return The iterator
     */
    public Iterator<ChannelMessage> getChannel(ChannelMessage.Channel c) {
        return messageQueue.get(c).iterator();
    }

    /**
     * Offers a {@link ChannelMessage} to a queue mapped to {@link ChannelMessage.Channel}
     *
     * @param c See {@link ChannelMessage.Channel}
     * @param m See {@link ChannelMessage}
     */
    public void offer(ChannelMessage.Channel c, ChannelMessage m) {
        messageQueue.get(c).offer(m);
    }

    @Handle
    private void handleHandshake(HandshakeMessage message) {
        switch (message.getState()) {
            case STATUS:
                //TODO Implement status protocol
                break;
            case LOGIN:
                game.getLogger().info("Handshake is LOGIN state, switching protocol");
                message.getSession().setProtocol(new LoginProtocol(game));
                game.getLogger().info("Protocol is now LOGIN state");
        }
    }

    @Handle
    private void handleLoginStart(LoginStartMessage message) {
        game.getLogger().info("Handling LoginStart");
    }
}

