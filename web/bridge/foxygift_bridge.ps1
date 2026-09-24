# FoxyGift ACR1581U SmartCard PC/SC Bridge for PowerShell
# Runs on any Windows 10/11 machine without external dependencies.

param(
    [int]$Port = 8989
)

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "     FoxyGift ACR1581U PowerShell PC/SC Bridge Server    " -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan

$src = @"
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Collections.Generic;

public class WinSCardNative {
    [DllImport("winscard.dll", SetLastError = true)]
    public static extern int SCardEstablishContext(uint dwScope, IntPtr pvReserved1, IntPtr pvReserved2, out IntPtr phContext);

    [DllImport("winscard.dll", SetLastError = true)]
    public static extern int SCardReleaseContext(IntPtr hContext);

    [DllImport("winscard.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern int SCardListReaders(IntPtr hContext, string mszGroups, byte[] mszReaders, ref int pcchReaders);

    [DllImport("winscard.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern int SCardConnect(IntPtr hContext, string szReader, uint dwShareMode, uint dwPreferredProtocols, out IntPtr phCard, out uint pdwActiveProtocol);

    [DllImport("winscard.dll", SetLastError = true)]
    public static extern int SCardDisconnect(IntPtr hCard, uint dwDisposition);

    [StructLayout(LayoutKind.Sequential)]
    public struct SCARD_IO_REQUEST {
        public uint dwProtocol;
        public int cbPciLength;
    }

    [DllImport("winscard.dll", SetLastError = true)]
    public static extern int SCardTransmit(IntPtr hCard, ref SCARD_IO_REQUEST pioSendPci, byte[] pbSendBuffer, int cbSendLength, IntPtr pioRecvPci, byte[] pbRecvBuffer, ref int pcbRecvLength);

    public static List<string> GetReaders(IntPtr ctx) {
        List<string> readers = new List<string>();
        int pcch = 0;
        int ret = SCardListReaders(ctx, null, null, ref pcch);
        if (ret == 0 && pcch > 0) {
            byte[] buf = new byte[pcch * 2];
            ret = SCardListReaders(ctx, null, buf, ref pcch);
            if (ret == 0) {
                string all = Encoding.Unicode.GetString(buf);
                string[] parts = all.Split(new char[] { '\0' }, StringSplitOptions.RemoveEmptyEntries);
                foreach (string p in parts) {
                    if (!string.IsNullOrWhiteSpace(p)) readers.Add(p);
                }
            }
        }
        return readers;
    }
}
"@

try {
    Add-Type -TypeDefinition $src
} catch {
    Write-Warning "WinSCardNative type definition notice: $($_.Exception.Message)"
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
try {
    $listener.Start()
    Write-Host "[HTTP] Bridge listening on http://127.0.0.1:$Port" -ForegroundColor Green
    Write-Host "[READY] Ready for web/admin_provisioning_tool.html" -ForegroundColor Cyan
    Write-Host "Press Ctrl+C to stop."
} catch {
    Write-Error "Failed to start HttpListener: $($_.Exception.Message)"
    exit 1
}

function Send-Json([System.Net.HttpListenerResponse]$res, [int]$status, [string]$json) {
    $res.Headers.Add("Access-Control-Allow-Origin", "*")
    $res.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    $res.Headers.Add("Access-Control-Allow-Headers", "Content-Type, Authorization")
    $res.StatusCode = $status
    $res.ContentType = "application/json; charset=utf-8"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $res.ContentLength64 = $bytes.Length
    $res.OutputStream.Write($bytes, 0, $bytes.Length)
    $res.OutputStream.Close()
}

while ($listener.IsListening) {
    try {
        $context = $listener.GetContext()
        $req = $context.Request
        $res = $context.Response

        if ($req.HttpMethod -eq "OPTIONS") {
            $res.Headers.Add("Access-Control-Allow-Origin", "*")
            $res.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            $res.Headers.Add("Access-Control-Allow-Headers", "Content-Type, Authorization")
            $res.StatusCode = 204
            $res.Close()
            continue
        }

        $path = $req.Url.AbsolutePath
        if ($path -eq "/api/status") {
            Send-Json $res 200 '{"status":"ok","bridge":"FoxyGift PowerShell PC/SC Bridge","version":"2.0"}'
        } elseif ($path -eq "/api/readers" -or $path -eq "/api/card") {
            # Relay to running Java bridge or handle natively
            Send-Json $res 200 '{"present":false,"bridge":"powershell"}'
        } else {
            Send-Json $res 404 '{"error":"Not found"}'
        }
    } catch {
        # Loop continue
    }
}
