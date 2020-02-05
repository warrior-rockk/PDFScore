/*
 BTPedal

 Pedal Bluetooth para cambiar de página PDF en aplicacion PDFScore (Prototipo con ATtiny85)

 Circuito
 * Pin 0 conectado a TX modulo BT
 * Pin 1 conectado a RX modulo BT con divisor resistivo para 3,3V
 * Pin 2 como entrada digital pullup para pulsador
 
 created 30/12/16
 by Warrior / Warcom Ing.

 */
 
#include <SoftwareSerial.h>

//pines
#define RX_PIN 0
#define TX_PIN 1
#define BT_PIN 2

//comandos BT
#define NEXT_CMD "NEXT"
#define PREV_CMD "PREV"

//delays
#define debounceDelay  100 
#define longClickDelay  500


//Variables generales
SoftwareSerial BT1(RX_PIN,TX_PIN); // Pin RX, TX de Arduino (cruzarlos con los del módulo BT)
//boton
int btState = HIGH;
long lastDebounceTime = 0;
long lastSingleClick = 0;
boolean btFlag = false;
boolean btLong = false;

void setup()
   {
       
       BT1.begin(9600);

       pinMode(BT_PIN,INPUT_PULLUP);
       
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
       
       //filtramos rebote
       if ( (millis() - lastDebounceTime) > debounceDelay) {
         //no pulsado (pullup)
         if ( btState == HIGH ) {
           //enviamos comando NEXT
           if (btFlag && !btLong)
           {
             BT1.write(NEXT_CMD);
             BT1.write("\r\n");
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
              BT1.write(PREV_CMD);
              BT1.write("\r\n");
              btLong = true;
            }
           }
           btFlag = true;
           
           lastDebounceTime = millis();
         }
       }

  }
