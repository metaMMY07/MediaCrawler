# Zhihu Android adapter

This adapter is a small Android port of the Zhihu search/session pieces from
MediaCrawler at `.reference/MediaCrawler` revision `8773e47`.  The source
files retain the upstream attribution and NON-COMMERCIAL LEARNING LICENSE 1.1
notice.  It is intended for learning and research, with conservative page
size and no login or captcha automation.

`ZhihuAdapter` receives three injected dependencies:

- `HttpTransport` performs the GET request.
- `cookieProvider` returns the current WebView cookie header.  Every request
  requires non-empty `d_c0` and `z_c0`; cookie values are never logged.
- `evaluate` evaluates an expression in a WebView that has already loaded
  `assets/signing/zhihu.js` and returns the raw
  `WebView.evaluateJavascript` result.

Signing follows the reference implementation but keeps MD5 in Kotlin:

1. `get_sign_input(path, cookie)` in the asset returns the exact plaintext
   assembled from `101_3_3.0`, the relative request path and query, `d_c0`,
   and the fixed `x-zst-81` value.
2. Kotlin computes MD5 over that plaintext's UTF-8 bytes.
3. `finish(md5Hex)` in the asset produces `x-zse-96`; `get_zst_81()` supplies
   `x-zst-81`.

The search URL and parameter order match
`media_platform/zhihu/client.py:get_note_by_keyword`:
`gk_version`, `t`, `q`, `correction`, `offset`, `limit`, `filter_fields`,
`lc_idx`, `show_all_topics`, `search_source`, `time_interval`, `sort`, and
`vertical`.  Values use form URL encoding, as Python `urlencode` does.

Requests also carry the fixed reference headers `accept`, `accept-language`,
`priority`, `referer`, `x-api-version`, `x-app-za`, `x-requested-with`, and
`x-zse-93`.  The host owns the WebView user agent so the HTTP path can use the
same UA as the logged-in WebView.

Search results are accepted only for answer, article, question, or video
objects with an id and title.  Unknown objects, malformed entries, and
duplicates are skipped.  The adapter maps the first title, author, excerpt,
link, thumbnail, and a useful count to `SearchItem`; its id is
`type:id` to avoid collisions, and it does not invent a placeholder result.

`verifySession()` signs `/api/v4/me?include=email%2Cis_active%2Cis_bind_phone`
and requires both `uid` and `name`.  Error kinds are kept separate:

- missing cookies, blank `uid`/`name`, or an explicit unauthenticated marker:
  `LOGIN_REQUIRED`;
- HTTP 403 or a challenge/captcha business response: `CHALLENGE`;
- HTTP 429: `RATE_LIMITED`;
- signature bridge/evaluation errors: `SIGNATURE`;
- transport failures: `NETWORK`;
- malformed JSON, missing or malformed user fields, malformed supported
  results, or an unrecognised business error: `PARSE`.

Cancellation exceptions are rethrown so coroutine cancellation is not turned
into a platform error.
