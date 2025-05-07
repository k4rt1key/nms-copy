package org.nms.API.Utility;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.ConsoleLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IpHelpers
{
    private static final int MAX_IP_COUNT = 1000;

    /**
     * Returns a JSON array of IPs based on the input IP and type
     *
     * @param ip IP address in appropriate format:
     *           - Single IP: "10.20.41.10"
     *           - Range: "10.20.41.10-10.20.41.15"
     *           - Subnet: "10.20.41.10/20"
     * @param ipType Type of IP representation ("SINGLE", "RANGE", or "SUBNET")
     * @return JsonArray containing IP addresses or empty JsonArray if invalid
     */
    public static JsonArray getIpListAsJsonArray(String ip, String ipType)
    {
        JsonArray jsonArray = new JsonArray();
        List<String> ipList = null;

        if (ip == null || ipType == null)
        {
            return jsonArray;
        }

        switch (ipType.toUpperCase())
        {
            case "SINGLE":
                if (isValidIp(ip))
                {
                    jsonArray.add(ip);
                }
                break;

            case "RANGE":
                if (ip.contains("-"))
                {
                    String[] range = ip.split("-");
                    if (range.length == 2)
                    {
                        String startIp = range[0].trim();
                        String endIp = range[1].trim();
                        ipList = getIpListForRange(startIp, endIp);
                    }
                }
                break;

            case "SUBNET":
                if (ip.contains("/"))
                {
                    String[] subnet = ip.split("/");
                    if (subnet.length == 2)
                    {
                        String ipPart = subnet[0].trim();
                        try
                        {
                            int prefixLength = Integer.parseInt(subnet[1].trim());
                            ipList = getIpListForSubnet(ipPart, prefixLength);
                        }
                        catch (NumberFormatException e)
                        {
                            // Invalid prefix length
                        }
                    }
                }
                break;

            default:
                // Invalid IP type
                break;
        }

        if (ipList != null && !ipList.isEmpty())
        {
            for (String ipAddress : ipList)
            {
                jsonArray.add(ipAddress);
            }
        }

        return jsonArray;
    }

    /**
     * Check if the given IP address is valid.
     *
     * @param ip
     * @return true if the IP address is valid, false otherwise
     */
    public static boolean isValidIp(String ip)
    {
        if (ip == null || ip.isEmpty())
        {
            return false;
        }

        try
        {
            InetAddress.getByName(ip);
            return true;
        }
        catch (UnknownHostException e)
        {
            return false;
        }
    }

    /**
     * Check if the given IP address and it's corresponding type are valid.
     *
     * @param ip
     * @param ipType
     * @return
     */
    public static boolean isValidIpAndType(String ip, String ipType)
    {
        if (ip == null || ipType == null)
        {
            return false;
        }

        switch (ipType)
        {
            case "SINGLE":

                // Supports both IPv4 and IPv6
                return isValidIp(ip);

            case "RANGE":

                // Supports both IPv4 and IPv6 ranges
                if (!ip.contains("-"))
                {
                    return false;
                }

                String[] range = ip.split("-");

                if (range.length != 2)
                {
                    return false;
                }

                String startIp = range[0].trim();

                String endIp = range[1].trim();

                if (!isValidIp(startIp) || !isValidIp(endIp))
                {
                    return false;
                }

                try
                {
                    // Ensure both IPs are of the same type (IPv4 or IPv6)
                    InetAddress startAddr = InetAddress.getByName(startIp);

                    InetAddress endAddr = InetAddress.getByName(endIp);

                    if (startAddr.getAddress().length != endAddr.getAddress().length)
                    {
                        return false;
                    }

                    // Check if startIp <= endIp and IP count <= 1000
                    var ipList = getIpListForRange(startIp, endIp);

                    return ipList != null && ipList.size() <= MAX_IP_COUNT;
                }
                catch (UnknownHostException e)
                {
                    return false;
                }

            case "SUBNET":
                // Supports IPv4 only (based on example like "10.20.40.1/22")
                if (!ip.contains("/"))
                {
                    return false;
                }

                String[] subnet = ip.split("/");

                if (subnet.length != 2)
                {
                    return false;
                }

                String ipPart = subnet[0].trim();

                if (!isValidIPv4(ipPart))
                {
                    return false;
                }

                try
                {
                    int prefixLength = Integer.parseInt(subnet[1].trim());

                    if (prefixLength < 0 || prefixLength > 32)
                    {
                        return false;
                    }

                    // Check if IP count <= 1000
                    long ipCount = 1L << (32 - prefixLength); // 2^(32-prefix)

                    if (ipCount > MAX_IP_COUNT)
                    {
                        return false;
                    }

                    // Optionally, generate IP list to validate
                    var ipList = getIpListForSubnet(ipPart, prefixLength);

                    return ipList != null && ipList.size() <= MAX_IP_COUNT;
                }
                catch (NumberFormatException e)
                {
                    return false;
                }

            default:
                return false;
        }
    }

    /**
     * Ping the given IP address
     *
     * @param ipArray
     * @return JsonArray of {"ip": "", "message": "", "success": true or false}
     */
    public static JsonArray pingIps(JsonArray ipArray)
    {
        ConsoleLogger.info("Recieved Pinging Request for " + ipArray);
        JsonArray results = new JsonArray();

        if (ipArray == null || ipArray.isEmpty())
        {
            return results;
        }

        try {
            // Prepare fping command: fping -c3 ip1 ip2 ...
            String[] command = new String[ipArray.size() + 2];
            command[0] = "fping";
            command[1] = "-c3";
            for (int i = 0; i < ipArray.size(); i++)
            {
                command[i + 2] = ipArray.getString(i);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null)
            {
                // Format: <ip> : xmt/rcv/%loss = 3/3/0%, min/avg/max = 2.34/4.56/6.78
                String[] parts = line.split(":");
                if (parts.length < 2) continue;

                String ip = parts[0].trim();
                String stats = parts[1].trim();

                JsonObject result = new JsonObject().put("ip", ip);

                if (stats.contains("100%"))
                {
                    result.put("message", "Ping check failed: 100% packet loss");
                    result.put("success", false);
                }
                else
                {
                    String avg = "";
                    if (stats.contains("min/avg/max"))
                    {
                        String[] latencyParts = stats.split("min/avg/max = ");
                        if (latencyParts.length == 2)
                        {
                            String[] latencyValues = latencyParts[1].split("/");
                            if (latencyValues.length >= 2)
                            {
                                avg = latencyValues[1] + " ms";
                            }
                        }
                    }
                    result.put("message", "Ping check success (avg latency: " + avg + ")");
                    result.put("success", true);
                }

                results.add(result);
            }

            process.waitFor();
        }
        catch (Exception e)
        {
            for (int i = 0; i < ipArray.size(); i++)
            {
                results.add(new JsonObject()
                        .put("ip", ipArray.getString(i))
                        .put("message", "Error: " + e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Check the TCP/UDP port on a given IP and return message
     *
     * @param ip   IP address to check
     * @param port Port number to check
     * @return JsonObject with the result
     */
    public static JsonObject checkPort(String ip, int port)
    {
        JsonObject result = new JsonObject().put("ip", ip).put("port", port);

        ConsoleLogger.info("Recieved Pinging Request for " + ip + "Port " + port);

        try
        {
            // Run the `nc` command to check if the port is open
            ProcessBuilder pb = new ProcessBuilder("nc", "-zv", ip, String.valueOf(port));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String message = "";

            boolean success = false;

            // Read the output from the process
            while ((line = reader.readLine()) != null)
            {
                // Check if the line contains a success message for the port
                if (line.contains("succeeded"))
                {
                    message = "Port " + port + " is open on " + ip;
                    success = true;
                }
                else if (line.contains("refused"))
                {
                    message = "Port " + port + " is closed on " + ip;
                }
            }

            // Add the message and success flag to the result object
            result.put("message", message);
            result.put("success", success);

            // Wait for the process to finish
            process.waitFor(5000, TimeUnit.MILLISECONDS);

        }
        catch (Exception e)
        {
            result.put("message", e.getMessage());
            result.put("success", false);
        }

        ConsoleLogger.info("Port check done with result " + result);
        return result;
    }

    /**
     * Check if the given IPv4 address is valid.
     *
     * @param ip
     * @return true if the IPv4 address is valid, false otherwise
     */
    public static boolean isValidIPv4(String ip)
    {
        if (ip == null || ip.isEmpty())
        {
            return false;
        }

        try
        {
            InetAddress inetAddress = InetAddress.getByName(ip);
            return inetAddress.getAddress().length == 4;
        }
        catch (UnknownHostException e)
        {
            return false;
        }
    }

    /**
     * Get list of IPs for a given range
     *
     * @param startIp
     * @param endIp
     * @return
     */
    public static List<String> getIpListForRange(String startIp, String endIp)
    {
        try
        {
            InetAddress startAddr = InetAddress.getByName(startIp);
            InetAddress endAddr = InetAddress.getByName(endIp);

            byte[] startBytes = startAddr.getAddress();
            byte[] endBytes = endAddr.getAddress();

            // Convert to BigInteger for comparison and iteration
            BigInteger startNum = new BigInteger(1, startBytes);
            BigInteger endNum = new BigInteger(1, endBytes);

            // Check if start <= end
            if (startNum.compareTo(endNum) > 0)
            {
                return null;
            }

            // Check if range size exceeds MAX_IP_COUNT
            BigInteger rangeSize = endNum.subtract(startNum).add(BigInteger.ONE);
            if (rangeSize.compareTo(BigInteger.valueOf(MAX_IP_COUNT)) > 0)
            {
                return null;
            }

            List<String> ipList = new ArrayList<>();

            BigInteger currentNum = startNum;
            while (currentNum.compareTo(endNum) <= 0)
            {
                byte[] ipBytes = bigIntegerToBytes(currentNum, startBytes.length);

                ipList.add(InetAddress.getByAddress(ipBytes).getHostAddress());

                currentNum = currentNum.add(BigInteger.ONE);
            }

            return ipList;

        }
        catch (UnknownHostException e)
        {
            return null;
        }
    }

    /**
     * Get list of IPs for a given subnet
     *
     *
     * @param ip
     * @param prefixLength
     * @return
     */
    public static List<String> getIpListForSubnet(String ip, int prefixLength)
    {
        try
        {
            InetAddress addr = InetAddress.getByName(ip);

            byte[] ipBytes = addr.getAddress();
            if (ipBytes.length != 4)
            {
                return null; // Only IPv4 supported
            }

            // Calculate number of IPs: 2^(32-prefix)
            long ipCount = 1L << (32 - prefixLength);
            if (ipCount > MAX_IP_COUNT)
            {
                return null;
            }

            // Convert IP to long
            long ipNum = bytesToLong(ipBytes);
            // Calculate network address (apply mask)
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            long network = ipNum & mask;

            List<String> ipList = new ArrayList<>();
            for (long i = 0; i < ipCount; i++)
            {
                long currentIp = network + i;

                byte[] currentBytes = longToBytes(currentIp);

                ipList.add(InetAddress.getByAddress(currentBytes).getHostAddress());
            }
            return ipList;
        }
        catch (UnknownHostException e)
        {
            return null;
        }
    }

    /**
     * Convert byte array to long (for IPv4)
     *
     * @param bytes
     * @return long
     */
    private static long bytesToLong(byte[] bytes)
    {
        long result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    /**
     * Convert long to byte array (for IPv4)
     *
     * @param value
     * @return byte[]
     */
    private static byte[] longToBytes(long value)
    {
        byte[] bytes = new byte[4];
        for (int i = 3; i >= 0; i--)
        {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }

    /**
     * Convert BigInteger to byte array with specified length
     *
     * @param num
     * @param length
     * @return
     */
    private static byte[] bigIntegerToBytes(BigInteger num, int length)
    {
        byte[] bytes = num.toByteArray();

        byte[] result = new byte[length];

        int srcOffset = Math.max(0, bytes.length - length);

        int destOffset = length - Math.min(bytes.length, length);

        System.arraycopy(bytes, srcOffset, result, destOffset, Math.min(bytes.length, length));

        return result;
    }
}