/*
 BTPedal

 Pedal Bluetooth para cambiar de página PDF en aplicacion PDFScore (ATtiny85)
 Una pulsacion corta envia un comando NEXT y una larga un comando PREV

 Circuito
 * Pin 2 conectado a TX modulo BT
 * Pin 1 conectado a RX modulo BT con divisor resistivo para 3,3V
 * Pin 0 como entrada digital pullup para pulsador
 * Pin 3 como salida para led de estado
 
 created 30/12/16
 by Warrior / Warcom Ing.
 v1.0
 */
 
#include <SoftwareSerial.h>

//pines
#define RX_PIN 2
#define TX_PIN 1
#define BT_PIN 0
#define LED_PIN 3

//comandos BT
#define NEXT_CMD "N"
#define PREV_CMD "P"
#define CHECK_CMD "C"
//respuestas BT
#define STATE_ST "OK"
#define DISCONNECT_ST "BYE"

//delays
#define debounceDelay  100 
#define longClickDelay  500
//parpadeo
#define ledBlinkDelay 500
//consulta estado
#define checkStatusDelay 30000

//Variables generales
SoftwareSerial BT1(RX_PIN,TX_PIN); // Pin RX, TX de Arduino (cruzarlos con los del módulo BT)

//boton
int btState = HIGH;
long lastDebounceTime = 0;
long lastSingleClick = 0;
boolean btFlag = false;
boolean btLong = false;
//estado
boolean paired = false;
long lastBlinkTime = 0;
long lastCheckTime = 0;
void setup()
   {
       
       BT1.begin(9600);

       pinMode(BT_PIN,INPUT_PULLUP);
       pinMode(LED_PIN,OUTPUT);
       
       lastCheckTime = millis();
       
       //Configuracion del modulo BT (descomentar para configurarlo automaticamente)
       /*
       delay(15000); //Esperar 15 segundos para conectar el modulo
       //nombre modulo  
       BT1.print("AT+NAMEBTPEDAL\n");
       delay(1000);
       //Pin (contraseña)
       BT1.print("AT+PIN0000\n");
       delay(1000);   
       //velocidad transmision (4 = 9600)  
       BT1.print("AT+BAUD4\n");
       delay(1000);
       */
       
   }
void loop()
   {
       //leemos pulsador
       btState = digitalRead(BT_PIN);

       
       //cada x tiempo,sin pulsacion de boton, comprobamos conexion 
       if ((millis() - lastCheckTime) > checkStatusDelay && btState==HIGH){
        BT1.write(CHECK_CMD"\r\n");
        paired = false;
        lastCheckTime = millis();
       }
       
       //esperamos conexion de la aplicacion
       if (BT1.available()){
           if (BT1.readString() == STATE_ST)
           {
              paired = true; 
              lastCheckTime = millis();
           }
           if (BT1.readString() == DISCONNECT_ST)
              paired = false; 
       }
              
       //actualizamos led (fijo si emparejado, parpadeo si no)
       if (paired){
        digitalWrite(LED_PIN,HIGH);
        lastBlinkTime = millis();
       }
       else if ((millis() - lastBlinkTime) > ledBlinkDelay)
       {
          digitalWrite(LED_PIN,!digitalRead(LED_PIN));
          lastBlinkTime = millis();
       }
       
       //filtramos rebote
       if ( (millis() - lastDebounceTime) > debounceDelay) {
         //no pulsado (pullup)
         if ( btState == HIGH ) {
           //enviamos comando NEXT
           if (btFlag && !btLong)
           {
             BT1.write(NEXT_CMD"\r\n");
             //BT1.write("\r\n");
           }
           
           btFlag = false;
           btLong = false;
           lastDebounceTime = millis();
         }
         //pulsado
         else if ( btState == LOW && !btLong )  {
           if (!btFlag){
            lastSingleClick = millis();
           }
           else
           {
            //si pulsacion larga, mandamos comando de PREV
            if ((millis() - lastSingleClick) > longClickDelay){
              BT1.write(PREV_CMD"\r\n");
              //BT1.write("\r\n");
              btLong = true;
            }
           }
           btFlag = true;
           
           lastDebounceTime = millis();
         }
       }

  }
