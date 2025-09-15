DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/1417222855082643638/c_xYaZivcDbeG8pYC1UONDhOam3nKvMBdrQqnPCsWBdggG_6tbvhizny0WhzMZsRTcR5

function botlog() {
    timestamp=$(date "+%F %T.%3N")
    curl -X POST -H 'Content-Type: application/json' -d "{\"content\":\"\`$timestamp\`$1\"}" $DISCORD_WEBHOOK_URL
}

botlog "This is a test message from the botlog function."