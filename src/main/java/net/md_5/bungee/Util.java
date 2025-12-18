package net.md_5.bungee;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Series of utility classes to perform various operations.
 */
public class Util
{

    private static final int DEFAULT_PORT = 25565;

    /**
     * Method to transform human readable addresses into usable address objects.
     *
     * @param hostline in the format of 'host:port'
     * @return the constructed hostname + port.
     */
    public static InetSocketAddress getAddr(String hostline)
    {
        String[] split = hostline.split( ":" );
        int port = DEFAULT_PORT;
        if ( split.length > 1 )
        {
            port = Integer.parseInt( split[1] );
        }
        return new InetSocketAddress( split[0], port );
    }

    /**
     * Normalizes a config path by prefix upper case letters with '_' and
     * turning them to lowercase.
     *
     * @param s the string to normalize
     * @return the normalized path
     */
    public static String normalize(String s)
    {
        StringBuilder result = new StringBuilder();
        for ( char c : s.toCharArray() )
        {
            if ( Character.isUpperCase( c ) )
            {
                result.append( "_" );
            }
            result.append( Character.toLowerCase( c ) );
        }
        return result.toString();
    }

    /**
     * Formats an integer as a hex value.
     *
     * @param i the integer to format
     * @return the hex representation of the integer
     */
    public static String hex(int i)
    {
        return String.format( "0x%02X", i );
    }

    /**
     * Constructs a pretty one line version of a {@link Throwable}. Useful for
     * debugging.
     *
     * @param t the {@link Throwable} to format.
     * @return a string representing information about the {@link Throwable}
     */
    public static String exception(Throwable t)
    {
        // TODO: We should use clear manually written exceptions
        StackTraceElement[] trace = t.getStackTrace();
        return t.getClass().getSimpleName() + " : " + t.getMessage()
                + ( ( trace.length > 0 ) ? " @ " + t.getStackTrace()[0].getClassName() + ":" + t.getStackTrace()[0].getLineNumber() : "" );
    }

    /**
     * Serializes address string to long
     *
     * @param address Remote client address
     * @return serialized address as long
     */
    public static long serializeAddress(String address)
    {
        String[] ipAddressInArray = address.split("\\.");

        long result = 0;

        // https://mkyong.com/java/java-convert-ip-address-to-decimal-number/
        for (int i = 0; i < ipAddressInArray.length; i++)
        {
            int power = 3 - i;
            int ip = Integer.parseInt(ipAddressInArray[i]);

            result += ip * Math.pow(256, power);
        }
        return result;
    }

    // BMC start
    public static int readVarInt(InputStream input) throws IOException {
        int value = 0;
        int i = 0;
        int b;
        while (((b = input.read()) & 0x80) != 0) {
            value |= (b & 0x7F) << i;
            i += 7;
            if (i > 35) {
                throw new IOException("VarInt bigger than maximum allowed 5 bytes");
            }
        }
        return value | (b << i);
    }

    public static String readUTF8(InputStream input) throws IOException {
        int length = readVarInt(input);
        byte[] bytes = new byte[length];
        input.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeVarInt(int value, OutputStream output) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value & 0x7F);
    }

    public static void writeUTF8(String value, OutputStream output) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= Short.MAX_VALUE) {
            throw new IOException("String length longer than maximum allowed 32767");
        }
        writeVarInt(bytes.length, output);
        output.write(bytes);
    }
    // BMC end

}
