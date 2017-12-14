
////VLC CONFIG////


#define TX_PIN_R (A1)
#define TX_PIN_G (A2)
#define TX_PIN_B (A3)
#define PERIOD (367)
#define PREAMBLE_LENGTH (5)
#define VLC_DATA_LEN (16)

uint32_t cntr = 0;
volatile uint16_t vlc_data = 0;

#define SENDING_PREAMBLE (0)
#define SENDING_DATA (1)


uint16_t hextobin(char* h)
{
  uint16_t ret = 0;
  if(h[0] >= 'A')
  {
    ret += (h[0]-'A'+10)*16*16*16;
  }
  else
  {
    ret += (h[0]-'0')*16*16*16;
  }

  if(h[1] >= 'A')
  {
    ret += (h[1]-'A'+10)*16*16;
  }
  else
  {
    ret += (h[1]-'0')*16*16;
  }

    if(h[2] >= 'A')
  {
    ret += (h[2]-'A'+10)*16;
  }
  else
  {
    ret += (h[2]-'0')*16;
  }

    if(h[3] >= 'A')
  {
    ret += (h[3]-'A'+10);
  }
  else
  {
    ret += (h[3]-'0');
  }

  return ret;
}



void setup() {
    pinMode(TX_PIN_R, OUTPUT);
    pinMode(TX_PIN_G, OUTPUT);
    pinMode(TX_PIN_B, OUTPUT);

    //initial blink
    digitalWrite(TX_PIN_R, LOW);
    digitalWrite(TX_PIN_G, LOW);
    digitalWrite(TX_PIN_B, LOW);
    delay(1000);
    digitalWrite(TX_PIN_R, HIGH);
    delay(500);
    digitalWrite(TX_PIN_R, LOW);
    digitalWrite(TX_PIN_G, HIGH);
    delay(500);
    digitalWrite(TX_PIN_G, LOW);
    digitalWrite(TX_PIN_B, HIGH);
    delay(500);
    digitalWrite(TX_PIN_B, LOW);
}


void vlc_transmitter_process(void)
{
  static int currentColor = TX_PIN_R;
  static int currentBit = 0;
  static int state = SENDING_PREAMBLE;
  int currentMicros = micros();
  static int lastMicros = 0;

  if((currentMicros -  lastMicros) < PERIOD)
    return;

  lastMicros = currentMicros;
    
  switch(state)
  {
    case SENDING_PREAMBLE:
      if(currentBit & 1) //odd bit
        digitalWrite(currentColor, HIGH);
      else
        digitalWrite(currentColor, LOW);

      currentBit++;

      if(currentBit >= PREAMBLE_LENGTH)
      {
        state = SENDING_DATA;
        currentBit = 0;
      }
      break;
    case SENDING_DATA:
      if((vlc_data>>currentBit)&1)
        digitalWrite(currentColor, HIGH);
      else
        digitalWrite(currentColor, LOW);

      currentBit++;
      if(currentBit >= VLC_DATA_LEN)
      {
          currentBit = 0;

          state = SENDING_PREAMBLE;


          cntr++;

          if(cntr > 800)
          {
            cntr = 0;
            digitalWrite(currentColor, LOW);
            switch(currentColor)
            {
              case TX_PIN_B:
                vlc_data = 0xE001;
                currentColor = TX_PIN_R;
                break;
              case TX_PIN_R:
                vlc_data = 0x1234;
                currentColor = TX_PIN_G;
                break;
              default:
               vlc_data = 0xABCD;
               currentColor = TX_PIN_B;
                break;
            }
          }
      }
      break;  
  }
}

void loop() {
	vlc_transmitter_process();  
}
