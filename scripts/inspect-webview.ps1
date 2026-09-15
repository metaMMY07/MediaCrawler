[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5580',
    [string]$Expression = 'JSON.stringify({url:location.href,title:document.title,ua:navigator.userAgent,width:innerWidth,bodyWidth:document.body.scrollWidth,text:document.body.innerText.slice(0,250),buttons:[...document.querySelectorAll("button,a,[role=button]")].filter(e=>/登录/.test(e.innerText)).slice(0,15).map(e=>({tag:e.tagName,cls:e.className,text:e.innerText.slice(0,50),href:e.getAttribute("href")}))})'
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Development emulator only.' }
$adb = 'C:\Users\30622\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$appProcessId = (& $adb -s $Serial shell pidof dev.mediasearch).Trim()
if ($appProcessId -notmatch '^\d+$') { throw 'Open a page in the debug app first.' }
& $adb -s $Serial forward tcp:9233 "localabstract:webview_devtools_remote_$appProcessId" | Out-Null
$pages = @((Invoke-WebRequest http://127.0.0.1:9233/json -UseBasicParsing).Content | ConvertFrom-Json)
$page = $pages | Where-Object { $_.url -match '^https://(www|m|passport)\.(bilibili|xiaohongshu|zhihu)\.com' } | Select-Object -First 1
if (-not $page) { throw 'No official visible page found in the debug WebView.' }
$socket = [Net.WebSockets.ClientWebSocket]::new()
$cancel = [Threading.CancellationTokenSource]::new(15000)
try {
    $socket.ConnectAsync([uri]$page.webSocketDebuggerUrl, $cancel.Token).GetAwaiter().GetResult() | Out-Null
    $payload = @{id=1;method='Runtime.evaluate';params=@{expression=$Expression;returnByValue=$true;awaitPromise=$true}} | ConvertTo-Json -Depth 5 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
    $socket.SendAsync([ArraySegment[byte]]::new($bytes),[Net.WebSockets.WebSocketMessageType]::Text,$true,$cancel.Token).GetAwaiter().GetResult() | Out-Null
    do {
        $stream = [IO.MemoryStream]::new()
        do {
            $buffer = [byte[]]::new(32768)
            $received = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),$cancel.Token).GetAwaiter().GetResult()
            $stream.Write($buffer,0,$received.Count)
        } while (-not $received.EndOfMessage)
        $message = [Text.Encoding]::UTF8.GetString($stream.ToArray()) | ConvertFrom-Json
        $stream.Dispose()
    } while ($message.id -ne 1)
    $message | ConvertTo-Json -Depth 8
} finally { $socket.Dispose(); $cancel.Dispose() }
