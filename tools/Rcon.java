import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Minimal Source-RCON client: java Rcon.java <host> <port> <password> <command...> (each further arg = one command). */
public class Rcon {
    public static void main(String[] a) throws Exception {
        try (Socket s = new Socket(a[0], Integer.parseInt(a[1]))) {
            s.setSoTimeout(15000);
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            send(out, 1, 3, a[2]);
            if (recv(in).id != 1) throw new IOException("auth failed");
            for (int i = 3; i < a.length; i++) {
                send(out, 10 + i, 2, a[i]);
                Packet p = recv(in);
                System.out.println("> " + a[i]);
                if (!p.body.isEmpty()) System.out.println(p.body);
            }
        }
    }
    static void send(OutputStream out, int id, int type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + 4 + 4 + b.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(4 + 4 + b.length + 2).putInt(id).putInt(type).put(b).put((byte) 0).put((byte) 0);
        out.write(buf.array()); out.flush();
    }
    record Packet(int id, int type, String body) {}
    static Packet recv(DataInputStream in) throws IOException {
        byte[] lenB = in.readNBytes(4);
        int len = ByteBuffer.wrap(lenB).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] rest = in.readNBytes(len);
        ByteBuffer buf = ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN);
        int id = buf.getInt(), type = buf.getInt();
        String body = new String(rest, 8, len - 10, StandardCharsets.UTF_8);
        return new Packet(id, type, body);
    }
}
