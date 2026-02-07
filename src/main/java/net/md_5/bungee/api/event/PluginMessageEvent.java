package net.md_5.bungee.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Cancellable;

/**
 * Event called when a plugin message is sent from a server to the proxy.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PluginMessageEvent extends TargetedEvent implements Cancellable {

    /**
     * Cancelled state.
     */
    private boolean cancelled;
    /**
     * Tag specified for this plugin message.
     */
    private final String tag;
    /**
     * Data contained in this plugin message.
     */
    private final byte[] data;

    public PluginMessageEvent(Server sender, ProxiedPlayer receiver, String tag, byte[] data) {
        super(sender, receiver);
        this.tag = tag;
        this.data = data;
    }

    // BMC start - remove plugin messaging from/to clients
    @Override
    public Server getSender() {
        return (Server) super.getSender();
    }

    @Override
    public ProxiedPlayer getReceiver() {
        return (ProxiedPlayer) super.getReceiver();
    }
    // BMC end

}
