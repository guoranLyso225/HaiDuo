<?php
// The local fixture store must never send real emails.
add_filter('pre_wp_mail', static function ($result, $attributes) { return true; }, 10, 2);
// Only for the loopback-bound local test store. Allow exactly the demo API callback.
add_filter('http_request_host_is_external', function ($external, $host, $url) {
    if ($host === 'haiduo-dev-api-1' && preg_match('#^http://haiduo-dev-api-1:8080/api/webhooks/woocommerce/[a-f0-9-]{36}$#', $url)) { return true; }
    return $external;
}, 10, 3);
