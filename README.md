Android's app that checks the next terror zone for Diablo 2 Resurrected.
- Checks API https://www.d2emu.com/api/v1/tz to see current, next and next_hour of terror zone.
- It shows the info and creates a foreground service and a notification with alarm:
  - At customizable minute, it will check every hour if selected terror zone is next and will make a notification with alarm sound (lasts 5 minutes in the notification bar). 
  - You can select inside app the terror zones to be alarmed with. If you check some of them and next terror zone matches with one of your selected zones, you will receive a notification with alarm sound.
- **Since d2emu API now requires authorization in headers, you must put your username and token inside Utils.kt** before building apk.
- Tested in Android 7, 10, 11, 12, 13 with Xiaomi and Realme phones.
- Based on the work from [Roberto Huertas](https://github.com/robertohuertasm), you can check a detailed info [here](https://robertohuertas.com/2019/06/29/android_foreground_services/) and [his repo](https://github.com/robertohuertasm/endless-service).

**WARNING**:
- You need to allow background data and no battery restrictions/optimizations in your phone.
- If you have a Realme phone you need to:
  - Bateria / Optimizar uso de bateria / no optimizar
  - Bateria / Espera optimizada / desactivado
  - App / Uso de bateria / permitir en segundo plano / permitir inicio automatico
  - App / Gestionar notificaciones / permitir todo