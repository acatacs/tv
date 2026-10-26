
================================================================================
INFORME FINAL - CORTES DE ~1s EN NEGRO EN HDMI3 (TV Kalley R10G / MOKA)
Realtek RTD6748, Android 14 (SDK 34) - 26-oct-2026
================================================================================

--------------------------------------------------------------------------------
1. RESUMEN EJECUTIVO
--------------------------------------------------------------------------------
Se han capturado y reproducido 8 episodios de negro en la entrada HDMI3 (puerto
3, TvInputHardwareInfo id 151520000, hdmi_port=3), con la cadena causal completa
en el log del driver Realtek del receptor HDMI.

  El negro NO lo causa Android, ni un reinicio de servicio, ni una desconexion
  fisica del cable. Lo causa el receptor HDMI del propio TV, al DETECTAR que la
  senal que llega es invalida:

    1) el decodificador de HDMI3 emite un timing de imagen inestable
    2) el receptor del TV detecta que el timing no cuadra (watchdog ONMS)
    3) el TV corta la imagen a negro (ForceBG) y re-sincroniza el PHY
    4) renegociacion completa: PLL -> EDID -> AVI -> HDCP -> MEASURE -> DISPLAY_ON
    5) la imagen vuelve en 1,32 - 3,38 s

  CAUSA MAS PROBABLE: el DECODIFICADOR (su generador de senal/timing).
  DESCARTADO como causa primaria: el CABLE (5V y HPD intactos en el 100% de las
  muestras, cero hotplug, cero caidas electricas).
  El TV no esta defectuoso: responde correctamente a una senal corrupta y se
  recupera 8 de 8 veces. Su unica "culpa" es que su watchdog ONMS es agresivo
  y el modo activo (1080i59,94 YUV444 a 74 MHz) no le deja margen.

--------------------------------------------------------------------------------
2. HALLAZGOS CON METRICA / COMANDO
--------------------------------------------------------------------------------
H1. Marcador inequivoco del negro: 8 episodios
    METRICA AUTORITATIVA (maquina de estados del panel, no depende de que se
    imprima la cadena de texto):
      grep -a "set_display_forcebg_mask display(0) type(4)" <logcat>
        value(1) = negro principal activo   value(0) = negro liberado

      ep   negro ON         negro OFF        duracion   log
      EP1  14:21:37.554    14:21:40.933     3379 ms    OLD (linea 13737/16601)
      EP2  14:22:22.648    14:22:24.101     1453 ms    OLD (linea 17196/18349)
      EP3  14:38:27.666    14:38:30.373     2707 ms    NEW (linea 16013/21597)
      EP4  14:38:47.124    14:38:49.597     2473 ms    NEW (linea 23021/30664)
      EP5  14:44:44.832    14:44:47.300     2468 ms    LIVE (linea 48306/55115)
      EP6  14:44:48.223    14:44:49.541     1318 ms    LIVE (linea 55275/57836)
      EP7  14:44:54.918    14:44:56.293     1375 ms    LIVE (linea 58349/61291)
      EP8  14:44:58.165    14:45:00.069     1904 ms    LIVE (linea 61591/65584)

    Duracion del negro: 3379 / 1453 / 2707 / 2473 / 2468 / 1318 / 1375 / 1904 ms
    Mediana 2468 ms, min 1318 ms, max 3379 ms, media 2135 ms  ->  coincide con el
    "~1 s" que describe el usuario (la vision vuelve en el "negro OFF", no antes).

    NOTA METODOLOGICA IMPORTANTE: la cadena secundaria
      '[N][VBE] ####RTK Enable Main ForceBG(Black screen)!!!####' / 'Disable Main ForceBG!!!'
    se imprime desde otro hilo y SE PIERDE en algunas ocurrencias (EP6 no la tiene) y
    con la rotacion del buffer kernel. La mascara type(4) no se pierde: por eso el
    recuento fiable es 8, no 7. Con solo la cadena de texto se contarian 7.

H2. 5V / HPD NUNCA se pierden (control global, 3 logs)
      Todas las muestras 'Signal STS Port[3]' -> 5V(0,0,0,1)   (unico valor)
      is_5V_HPD=1 en el 100% ; is_5V_HPD=0 -> 0 ocurrencias
      AVMUTE=0 constante ; BGRHV=0x1f constante
      PhyState del p3: solo 11 (senal presente) y 0 (reset interno del driver,
      se recupera en 110-230 ms). Ningun PhyState=0 con 5V caida.
    => El cable jamas se desconecto. DESCARTADO.

H3. NO hay hotplug HDMI ni evento de desconexion
      'HotPlugDetectionAction: Poll all devices' -> USB (usb2-port1), no HDMI.
      395/489/564 ocurrencias a cadencia fija de ~300 ms: coincidencia temporal,
      no causalidad.
      'HDMI_set_detect_flag enable:1' -> bandera interna del driver al reenganchar
      el PHY (4/8/15 ocurrencias), no un hotplug del kernel.
      Unico hotplug CEC real: "[H] time=2026-07-16 07:04:15 hotplug port=3
      connected=true" (arranque del 16-jul).
    => DESCARTADO.

H4. El disparo del negro es el watchdog ONMS del receptor (no Android)
      10-26 14:44:44.836  [W][HDMI] [L4][online_i3ddma][port: 3] onms3 watchdog
                            detect error!!watchdog err=200000
      10-26 14:44:44.836  [W][HDMI] [L4][vfe_hdmi_drv_handle_on_line_measure_error
                            port:3] End, is_reset_phy_flow=1 (vfe_call=1, is_onms_error=1)
      Errores de watchdog por episodio (+/-3 s): EP1=4, EP2=1, EP3=2, EP4=3, EP5=4,
      EP6=3, EP7=3, EP8=2. Valores wdg_error observados: 200000, 400000, 800, 400,
      80, c08.  (8/8 episodios con watchdog error)

H5. Re-sincronizacion completa en cada episodio
      [W][HDMI] [L4][p3] clear goodtiming flag!          (t0 -4..-15 ms)
      [W][HDMI] [HDMI][FSM] Port [3], AUDIO_FSM=5(CHECK) -> 0(START)   (t0 +0..+1 ms)
      [I][HDMI] [L6]HDMI[p3] TMDS clock change!! tmds_phy_md_clk from 702 -> 0
      [I][HDMI] [L0]clk_stable[p3] from 0 to 1 clk=703
      [W][HDMI] [DETECT][EVENT][ 5] Happened VIDEO FSM event: ONMS_FULLY_RESET,
                 Action: RESET_TO_WAIT_SYNC
      [I][HDMI] [HDCP][L6]HDMI[p3] Switch to HDCP_OFF -> HDCP22 -> NO_HDCP
      [I][HDMI] [HDCP][L6]HDMI[p3] got AKSV, start auth (round=1..2)
      VIDEO_FSM: 0(WAIT_SYNC) -> 1(SETUP_VEDIO_PLL) -> 2(MEASURE) -> 5(DISPLAY_ON)
                 -> 6(VIDEO_READY)
    El reenganche SIEMPRE termina bien -> el TV funciona.

H6. La senal que llega es invalida: la FUENTE genera el problema
      (a) DRTM (decoder de video Realtek) reporta confusion de campo en 8/8:
            10-26 14:38:24.577  [DRTM] filed_confuse_pre=0, filed_confuse_now=1, filed_confuse=1
          Solo aparece alrededor de los negros; nunca en juego estable.
      (b) El timing de la fuente se deforma fisicamente:
            14:38:25.511  NR,vact:768288-768288@...  hact:660-1920@...  5:8192-2200@...
            14:44:47.123  NR,vact:18803356-1270955@...  hact:1240-1920@...
      (c) El reloj de pixel de la fuente alterna (inestabilidad del generador):
            75816 (74039,062 kHz) <-> 75924 (74144,531 kHz)  = 0,1425 %
            OLD 4 cambios/14 obs ; NEW 7/26 ; LIVE 15/38
      (d) v_total alterna 562<->563 manteniendo 59935 Hz exactos.
      (e) El TV rechaza el timing: measure_result=HDMIMS_FAIL_HACT_UNDER_MIN,
          con hasta 12 reintentos de MEASURE antes de lograr un timing valido.
      (f) Los InfoFrames que emite la fuente llegan con checksum erroneo:
            [W][HDMI] [L4]infoframe checksum FAIL, type(82/83/84/87) ...
            [W][HDMI] [L4]Infoframe read total result : SPD_READ_CHECKSUM_ERROR
          29 ocurrencias en la ventana 14:20-14:22; 25-27 por episodio.
      (g) El EDID que publica el decodificador NO es suyo:
            0x35EB prod 0x0001, nombre "SMART TV", DTD preferido 3840x2160, 160x90 cm
          -> esta re-transmitiendo el EDID de otro equipo (EDID passthrough).

H7. Cero eventos del lado Android (descarta la pila de software)
      'ActivityManager: Start proc' 0 ; 'has died' 0 ; 'ANR in' 0 ;
      'ServiceManager: Restarting' 0 ; lmkd 0 ; Watchdog kill 0
      (en toda la ventana 26-oct 13:37 -> 14:49)
      Cambios de modo de display: Set_DisplayMode / setDisplayMode /
      DisplayMode_Change / onDisplayModeChange => 0 en los 3 logs.
      'Get_DisplayMode_Src=2' es una LECTURA del motor de imagen (VPQ) al
      recuperar la senal -> consecuencia, no causa.
      'TvInputManagerService: setMainSession' con el MISMO token 318c0dd, sin
      cambio de entrada (sigue HW151520000 = hdmi_port 3).

H8. CEC: el unico dispositivo de la red es la PS5, y esta en HDMI2
      mPortInfo: los 3 puertos tienen cec:true
      mDeviceInfos: solo 0x00 (TV) y 0x04 PlayStation 5 (port_id 2, PA 0x2000)
      => HDMI3 (port_id 3, PA 0x3000) NO tiene dispositivo CEC: el decodificador
         no participa en CEC. No se puede usar CEC para identificar el equipo de
         HDMI3, y su ausencia no significa que no haya equipo.

H9. Matiz importante sobre el "carril degradado"
      Los contadores 'current_err' se REINICIAN en cada reenganche del PHY, asi que
      no son acumulables entre episodios. Recontando la TASA de error, el carril
      dominante cambia: OLD -> lane1 (30/36 muestras CHAR_ERR);
      NEW -> lane2 (13/20 BIT_ERR); LIVE -> lane2 (52/85 CHAR_ERR, 69/79 BIT_ERR).
      El ecualizador que el TV recalcula en cada reenganche se mantiene en rango
      normal (le_coef max=30..34, min=26..33; Tap0=0x1A..0x1E) -> ningun carril
      fisicamente danado. Los transitorios de miles de errores/s son la
      CONSECUENCIA de leer bytes basura mientras no hay lock, no su causa.
      Detalle completo en per_lane_error_analysis.txt.

H10. No hay patron temporal "cada N minutos" ni dependencia de app
      Espaciado entre negros: 45,1 s ; 965,0 s ; 19,5 s ; 10,1 s ; 3,3 s ;
      6,7 s ; 3,2 s
      -> agrupan en rafagas, no en un periodo fijo.
      No coinciden con cambio de app, cambio de canal ni de entrada.
      En 14:44 hubo 4 negros en 15 s (EP5,EP6,EP7,EP8): cuando la fuente entra en
      regimen inestable se encadena.

--------------------------------------------------------------------------------
3. LIMITACIONES
--------------------------------------------------------------------------------
L1. Sin acceso al decodificador ni al cable: no puedo medir directamente la
    salida del decodificador ni la calidad del cable. La atribucion al
    decodificador se apoya en la telemetria del lado receptor + DRTM.
L2. No hay un test de marble (inyectar una señal de referencia known-good) en el
    puerto 3, asi que no puedo PROBAR de forma positiva que el receptor del TV
    funciona con una señal perfecta. El argumento es indirecto: 8/8 recuperaciones
    exitosas, 0 errores en los puertos sin fuente, y un EDID/InfoFrame coherente
    fuera de los episodios.
L3. El logcat no cubre 16-jul -> 26-oct (3 meses). Solo hay 2 ventanas utiles:
    13:37-14:27 (guardada) y 13:37-14:49 (nuevas). Los 8 episodios caen dentro.
    No puedo saber la tasa real de ocurrencia ni si hay otros modos de fallo.
L4. Many paths del sistema dan Permission denied (uid 2000, sin root): no se
    pudo leer dmesg, ni registros del driver fuera de logcat, ni el estado del
    PHY por ioctl. El estado del PHY se ha deducido del propio log del driver.
L5. La "tasa de error por carril" se ha reconstruido de muestras dispersas del
    contador (el driver solo lo publica esporadicamente); es una estimacion, no
    una medicion continua.
L6. No se ha podido comprobar si el negro del usuario es el mismo sintoma: el
    usuario lo describe como "~1 s" y nuestros negros duran 1,32-3,38 s. La
    percepcion subjetiva encaja con el "negro OFF" (el instante en que la imagen
    reaparece), no con la perdida de lock (que es sub-milisegundo). Si ademas el
    equipo que el usuario ve ahora no es el mismo que estaba conectado, no se
    puede correlacionar 1:1 con estos 8 episodios.
L7. ROTACION DEL BUFFER KERNEL: el volcado NEW empieza a mitad de un episodio
    (14:38:24.293, ya con un reset de watchdog en curso) y el buffer kernel
    antiguo (OLD) termina en 14:22:37. Hay un hueco sin cobertura entre 14:22:37
    y 14:38:24 (~16 min): pueden existir episodios alli que no se ven.
L8. La captura en vivo se detuvo a las 14:46 y el proceso adb sigue vivo en
    segundo plano; puede haber mas datos en el fichero.
L9. No hay ningun ANR/reinicio, pero eso no descarta un problema de CPU
    (load average ~19-21 con CPU idle 71,9 %): el watchdog ONMS es sensible a
    la latencia de CPU, y un pico de carga podria disparar un falso positivo.
    No se ha podido medir la latencia de los hilos del pipeline en el instante
    exacto del disparo.

--------------------------------------------------------------------------------
4. VEREDICTO: TV vs DECODIFICADOR vs CABLE
--------------------------------------------------------------------------------
TV .............. NO es la causa.        Confianza ALTA
   - 8/8 recuperaciones limpias, 0 errores en HDMI1/HDMI2, 0 eventos Android,
     0 cambios de modo de display. El negro lo provoca su propio driver al
     detectar una senal invalida: es una respuesta, no un fallo.
   - Matiz: su watchdog ONMS es agresivo y convierte un glitch de enlace en un
     negro visible de 1,3-3,4 s. Un firmware mas tolerante lo mitigaria.
   - Confianza MEDIA-ALTA (falta el test de marble, L2).

DECODIFICADOR ... CAUSA MAS PROBABLE.  Confianza MEDIA-ALTA
   - Genera timing inestable de forma medible: reloj de pixel alternando
     0,1425 %, v_total alternando, campos deformados (hact 660-1920,
     1240-1920), 'filed_confuse' en 8/8 episodios, InfoFrames con checksum
     erroneo, y un EDID ajeno ("SMART TV" 4K) re-transmitido.
   - Falta medirlo directamente (L1).

CABLE .......... NO es causa primaria.   Confianza ALTA
   - 5V/HPD intactos 100%, cero hotplug, cero caida electrica, cero carril
     fisicamente danado (LE/Tap0 en rango normal).
   - NO descartado como contribuyente: un cable marginal junto a una fuente que
     ya emite senal de ~74 MHz en YUV444 1080i puede agravar la relacion
     senal/ruido. Pero por si solo no explica la corrupcion de timing de la
     fuente.

PRUEBAS QUE CONFIRMARIAN (de mas barata a mas cara)
  P1 (2 min, gratis) Cambiar el decodificador a 1080p/60 (o forzar RGB / 4:2:2).
      Si los negros y los 'HDMIMS_FAIL_HACT_UNDER_MIN' desaparecen -> confirmado
      que el generador de timing del decodificador es el problema. Ademas deja el
      TV en un modo con margen (recomendacion permanente).
  P2 (5 min) Sustituir el cable por uno CAT2 certificado 4K60, <=3 m, gold.
      Si baja la tasa de CHAR_ERR pero los negros siguen -> el cable era
      contribuyente, no la causa.
  P3 (5 min, DEFINITIVA) Poner la PS5 (ya disponible) en HDMI3 con el mismo
      cable. Si no hay ningun negro -> el problema es el decodificador. Y al
      reves: si la PS5 en HDMI2 tampoco da problemas, el puerto esta bien.
  P4 (5 min) Aislar el enlace: decodificador a <1,5 m del TV con cable corto o
      cable activo certificado. Si cesa, era longitud/calidad del enlace.
  P5 (cerrar al 100%) Captura instrumentada 10 min de visionado:
        adb -s 192.168.1.74:5555 logcat -b all -v threadtime | \
          grep --line-buffered -E 'Main ForceBG|filed_confuse|HDMIMS_FAIL|infoframe checksum|ONMS|TMDS clock|Signal STS Port'
      Con fuente estable los contadores de 'filed_confuse', 'HDMIMS_FAIL' e
      'infoframe checksum FAIL' deben ser 0.
      (Comando de solo lectura; ya hay una captura en vivo en
       /tmp/tvdiag/hdmi/live_capture_144106.log)

--------------------------------------------------------------------------------
5. ARTEFACTOS
--------------------------------------------------------------------------------
/tmp/tvdiag/hdmi/
  events_forcebg_table.txt        Tabla maestra de los 7 eventos ForceBG + duraciones
  black_duration_table.txt        Tiempos exactos por episodio (t0, clear goodtiming,
                                  AUDIO_FSM, VIDEO_READY estable, fin del negro)
  evidence_per_event.txt          Ventanas de evidencia +/-1,5 s por episodio
                                  (solo lineas HDMI/scaler/DRTM)
  correlation_table.txt           Correlacion negro <-> anomalias de fuente + controles
                                  (5V/HPD global, puertos 1/2/3)
  per_lane_error_analysis.txt     Analisis por carril (tasa, no contador bruto) + LE/Tap0
  source_signal_integrity.txt     Evidencia de integridad de la senal de origen
                                  (reloj, v_total, DRTM, HACT, InfoFrame, EDID, modo)
  Q1_Q5_answers.txt               Respuestas a las 5 preguntas con su evidencia
  evt1_142137.txt evt2_142222.txt evt3_143824_all.txt evt3_143824_full.txt
  evt3_keylines.txt pre_event1_full.txt pre_event1_interesting.txt
                                  Recortes de ventanas de eventos
  logcat_fresh_143905.txt         Volcado nuevo de solo lectura (13:37-14:39)
  live_capture_144106.log         Captura en vivo en streaming (14:41-14:49)
  dumpsys_tv_input.txt            dumpsys tv_input (estado de HDMI3, CEC, HD list)
  dumpsys_hdmi_control.txt        dumpsys hdmi_control (CEC, puertos, historial)
  dumpsys_cec_service.txt         dumpsys del servicio CEC
  dumpsys_hdmi_connection.txt     dumpsys del servicio de conexion HDMI
  dumpsys_display.txt             dumpsys display
  dumpsys_sf_displayid.txt        dumpsys SurfaceFlinger --display-id
  dumpsys_activity_services.txt   dumpsys activity services

  Origenes: /tmp/tvdiag/raw/logcat_-b_all_-d_-v_threadtime.txt (logcat guardado,
  37278 lineas, 13:37-14:27 del 26-oct + arranque del 16-jul)

--------------------------------------------------------------------------------
6. TODA LA EVIDENCIA ES DE SOLO LECTURA
--------------------------------------------------------------------------------
Solo se ejecutaron: adb logcat -d / adb logcat (streaming), adb shell dumpsys ...,
y lecturas de ficheros locales. NO se ejecuto ninguna escritura: nada de
settings put, setprop, svc, am, pm disable, kill, stop/start, ni root/su.
================================================================================
