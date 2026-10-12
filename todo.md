# todo
# За официален Viber Chatbot стандартната публикувана базова такса е 115 € на месец за бот, като според договора и типа съобщения може да има допълнителни разходи.

Cineland Monitor с Viber щеше да стане по следния начин: Java приложението продължава да проверява Cineland API, но вместо Telegram getUpdates използва Viber webhook, достъпен през Cloudflare Tunnel.
Най-важното е, че не ти трябва VPS — можеш да го пуснеш на собствения си компютър.
1. Първо ти трябва Viber бот
   Трябва да имаш активен Viber бот с authentication token. Това е основното ограничение: новите Viber ботове се предоставят при търговски условия, така че не е достатъчно просто да инсталираш Viber и да си създадеш безплатен бот като в Telegram.
   Ако вече имаш токен, останалата част можеш да реализираш с Java и Cloudflare Tunnel.
2. Java приложението приема webhook
   Например локално на:
   http://localhost:8080/webhook


Когато потребител напише команда във Viber, Viber изпраща POST заявка до този endpoint.
3. Пускаш Cloudflare Tunnel
   cloudflared tunnel --url http://localhost:8080


Получаваш публичен адрес, например:
https://example.trycloudflare.com


Тогава webhook адресът ти ще бъде:
https://example.trycloudflare.com/webhook


Това е адресът, който регистрираш във Viber.
4. Регистрираш webhook във Viber
   След като Java сървърът и Cloudflare Tunnel работят, изпълняваш:
   curl -X POST https://chatapi.viber.com/pa/set_webhook \
   -H "Content-Type: application/json" \
   -H "X-Viber-Auth-Token: YOUR_VIBER_TOKEN" \
   -d '{
   "url": "https://example.trycloudflare.com/webhook",
   "event_types": []
   }'


При успех Viber връща:
{
"status": 0,
"status_message": "ok"
}


Viber проверява webhook адреса, като изпраща тестова заявка и очаква HTTP 200.

Viber Developers Hub
+1



5. Обработваш командите и изпращаш известия
   В Java ще трябва да добавиш:
- HTTP сървър на порт 8080, който приема POST /webhook.
- Проверка на X-Viber-Content-Signature чрез HMAC-SHA256, за да удостовериш входящите заявки.
- Обработка на команди като /date и /status.
- Изпращане на съобщения чрез POST https://chatapi.viber.com/pa/send_message.
  За изпращането ще ти трябва и Viber user ID на получателя. Потребителят трябва предварително да е започнал разговор или да се е абонирал за бота.

Viber Developers Hub



Самият Cineland scheduler и сравнението на JSON файловете могат да останат почти непроменени.

# Cloudflare Tunnel
Cloudflare:
1. Създаваш постоянен тунел
   cloudflared tunnel login
   cloudflared tunnel create cineland-viber


2. Свързваш поддомейна с тунела
   cloudflared tunnel route dns cineland-viber viber.example.com


3. Създаваш конфигурация ~/.cloudflared/config.yml:
   tunnel: YOUR_TUNNEL_UUID
   credentials-file: /home/sunny/.cloudflared/YOUR_TUNNEL_UUID.json

ingress:
- hostname: viber.example.com
  service: http://localhost:8080
- service: http_status:404


4. Стартираш тунела
   cloudflared tunnel run cineland-viber


5. Регистрираш webhook във Viber
   curl -X POST https://chatapi.viber.com/pa/set_webhook \
   -H "X-Viber-Auth-Token: YOUR_VIBER_TOKEN" \
   -H "Content-Type: application/json" \
   -d '{
   "url": "https://viber.example.com/webhook"
   }'


Тук Java приложението вече трябва да слуша на порт 8080 и да връща HTTP 200 за валидната Viber webhook проверка. За реална работа трябва и да проверява подписа на входящите заявки.
Защо този вариант е по-добър?
Получаваш постоянен адрес https://viber.example.com/webhook, който няма да се променя при рестартиране на тунела. Cloudflare осигурява публичния HTTPS достъп, а Java приложението може да остане на домашния ти компютър.
Единственото условие е компютърът, Java приложението и тунелът да работят, за да получаваш съобщения.
Но има едно отделно изискване: домейнът и Cloudflare Tunnel решават мрежовата част, не достъпа до Viber Bot API. За него пак ти трябва активен Viber бот с authentication token.