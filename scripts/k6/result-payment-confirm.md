docker compose run --rm k6 run `
>> -e BASE_URL=http://app:8080 `
>> -e VUS=30 `
>> -e ITERATIONS=30 `
>> -e PAYMENT_KEY=pk_k6_lock_test `
>> /scripts/payment-confirm-contention.js

[+] Creating 4/4
✔ Container minishop-redis    Running                                                                                                                                      0.0s
✔ Container minishop-mysql    Running                                                                                                                                      0.0s
✔ Container minishop-pg-mock  Running                                                                                                                                      0.0s
✔ Container minishop-app      Running                                                                                                                                      0.0s
[+] Running 2/2
✔ Container minishop-mysql  Healthy                                                                                                                                        0.5s
✔ Container minishop-redis  Healthy                                                                                                                                        0.5s

         /\      Grafana   /‾‾/  
    /\  /  \     |\  __   /  /   
/  \/    \    | |/ /  /   ‾‾\
/          \   |   (  |  (‾)  |
/ __________ \  |_|\_\  \_____/

     execution: local
        script: /scripts/payment-confirm-contention.js
        output: -

     scenarios: (100.00%) 1 scenario, 30 max VUs, 2m30s max duration (incl. graceful stop):
              * confirm_contention: 30 iterations shared among 30 VUs (maxDuration: 2m0s, gracefulStop: 30s)


     ✗ confirm status is 200
      ↳  0% — ✓ 0 / ✗ 30
     ✗ confirm success true
      ↳  0% — ✓ 0 / ✗ 30

     █ setup

       ✓ register status is 200
       ✓ login status is 200
       ✓ login token exists
       ✓ product status is 200
       ✓ product id exists
       ✓ add stock status is 200
       ✓ order status is 200
       ✓ order id exists
       ✓ prepare status is 200
       ✓ tossOrderId exists
       ✓ amount exists

✗ checks.........................: 15.49% 11 out of 71
confirm_duration_ms............: avg=461.909838 min=407.339111 med=462.970192 max=507.225197 p(90)=499.491136 p(95)=503.120741
confirm_unexpected_failure.....: 30     30.035/s
data_received..................: 17 kB  17 kB/s
data_sent......................: 18 kB  18 kB/s
http_req_blocked...............: avg=1.53ms     min=5.12µs     med=489.07µs   max=14.17ms    p(90)=2.43ms     p(95)=9.14ms    
http_req_connecting............: avg=1ms        min=0s         med=358.92µs   max=10.65ms    p(90)=1.66ms     p(95)=3.91ms    
http_req_duration..............: avg=398.13ms   min=32.35ms    med=455.65ms   max=507.22ms   p(90)=496.71ms   p(95)=502.14ms  
{ expected_response:true }...: avg=79.23ms    min=32.35ms    med=73.16ms    max=142.91ms   p(90)=131.74ms   p(95)=137.33ms  
http_req_failed................: 83.33% 30 out of 36
http_req_receiving.............: avg=1.55ms     min=95.51µs    med=1.07ms     max=5.77ms     p(90)=3.42ms     p(95)=4.35ms    
http_req_sending...............: avg=1.03ms     min=20.54µs    med=220.55µs   max=13.9ms     p(90)=2.03ms     p(95)=5.99ms    
http_req_tls_handshaking.......: avg=0s         min=0s         med=0s         max=0s         p(90)=0s         p(95)=0s        
http_req_waiting...............: avg=395.54ms   min=31.26ms    med=453.98ms   max=505.79ms   p(90)=492.89ms   p(95)=500.75ms  
http_reqs......................: 36     36.042001/s
iteration_duration.............: avg=463.89ms   min=409.02ms   med=467.11ms   max=511.48ms   p(90)=500.48ms   p(95)=505.85ms  
iterations.....................: 30     30.035/s
vus............................: 3      min=3        max=3
vus_max........................: 30     min=30       max=30


running (0m01.0s), 00/30 VUs, 30 complete and 0 interrupted iterations
confirm_contention ✓ [======================================] 30 VUs  0m00.5s/2m0s  30/30 shared iters
ERRO[0001] thresholds on metrics 'checks' have been crossed 
