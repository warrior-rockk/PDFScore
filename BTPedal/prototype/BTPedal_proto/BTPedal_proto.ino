/*
 BTPedal

 Pedal Bluetooth para cambiar de página PDF en aplicacion PDFScore (Prototipo en arduino)

 Circuito
 * Pin 2 conectado a TX modulo BT
 * Pin 3 conectado a RX modulo BT con divisor resistivo para 3,3V
 * Pin 4 como entrada digital pullup para pulsador
 
 created 30/12/16
 by Warrior / Warcom Ing.

 */
 
#include <SoftwareSerial.h>

//pines
#define RX_PIN 2
#define TX_PIN 3
#define BT_PIN 4

//comandos BT
#define NEXT_CMD "NEXT"
#define PREV_CMD "PREV"

//Variables generales
SoftwareSerial BT1(RX_PIN,TX_PIN); // Pin RX, TX de Arduino (cruzarlos con los del módulo BT)
//boton
int btState = HIGH;
long lastDebounceTime = 0;
boolean btFlag = false;
const long debounceDelay = 300; 

void setup()
   {
       Serial.begin(9600);
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
           btFlag = false;
           lastDebounceTime = millis();
         }
         //pulsado
         else if ( btState == LOW )  {
           //enviamos comando NEXT
           if (!btFlag)
           {
             BT1.write(NEXT_CMD);
             BT1.write("\r\n");
             Serial.write(NEXT_CMD);
             Serial.write("\n");
           }
           btFlag = true;
           
           lastDebounceTime = millis();
         }
       }

       //DEBUG SERIE
       //===========

       //si Bluetooth con datos, escribimos lo que leemos en el puerto serie de Arduino
       if (BT1.available())
       {
           Serial.write(BT1.read());
       }
       
       //si serie con datos, enviamos a BT lo que leemos por serie de Arduino
       if (Serial.available())
          {  
             //ojo, necesita retorno de carro \n al final de la línea. La consola serie de Arduino
             //permite enviar todo de golpe y añadir retorno de carro al final
             BT1.print(Serial.read());
          }
  }
