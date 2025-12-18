package net.md_5.bungee.protocol.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.packet.PacketHandler;
import net.md_5.bungee.protocol.PacketDefinitions;
import net.md_5.bungee.protocol.PacketDefinitions.OpCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PacketReader
{

    private static final Instruction[][] instructions = new Instruction[ PacketDefinitions.opCodes.length ][];

    static
    {
        for ( int i = 0; i < instructions.length; i++ )
        {
            List<Instruction> output = new ArrayList<>();

            OpCode[] enums = PacketDefinitions.opCodes[i];
            if ( enums != null )
            {
                for ( OpCode struct : enums )
                {
                    try
                    {
                        output.add( (Instruction) Instruction.class.getDeclaredField( struct.name() ).get( null ) );
                    } catch ( NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex )
                    {
                        throw new UnsupportedOperationException( "No definition for " + struct.name() );
                    }
                }

                List<Instruction> crushed = new ArrayList<>();
                int nextJumpSize = 0;
                for ( Instruction child : output )
                {
                    if ( child instanceof Jump )
                    {
                        nextJumpSize += ( (Jump) child ).len;
                    } else
                    {
                        if ( nextJumpSize != 0 )
                        {
                            crushed.add( new Jump( nextJumpSize ) );
                        }
                        crushed.add( child );
                        nextJumpSize = 0;
                    }
                }
                if ( nextJumpSize != 0 )
                {
                    crushed.add( new Jump( nextJumpSize ) );
                }

                instructions[i] = crushed.toArray( new Instruction[ crushed.size() ] );
            }
        }
    }

    // BMC - change signature
    public static void readPacket(ByteBuf in, HandlerBoss handlerBoss) throws IOException
    {
        // BMC start
        in.markReaderIndex();
        int packetId = in.readUnsignedByte();

        PacketHandler packetHandler = handlerBoss.getHandler();
        if (packetHandler instanceof InitialHandler) {
            InitialHandler initialHandler = (InitialHandler) packetHandler;
            if (packetId > 2 || initialHandler.isRequestedStatus()) {
                in.resetReaderIndex();
                initialHandler.handleQuery(new ByteBufInputStream(in));
                return;
            }
        }
        // BMC end

        Instruction[] packetDef = null;
        if ( packetId < instructions.length )
        {
            packetDef = instructions[packetId];
        }

        if ( packetDef == null )
        {
            ProxyServer.getInstance().getLogger().info("Unknown packet id " + packetId); // BMC - make less verbose
            return;
        }

        for ( Instruction instruction : packetDef )
        {
            instruction.read( in );
        }
    }

}
