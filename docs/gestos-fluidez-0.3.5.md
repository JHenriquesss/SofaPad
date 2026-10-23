# Cursor sem trancos e gestos do touchpad — 0.3.5

Só o APK muda. O agente Windows continua na 0.3.4 e o protocolo não mudou.

## Problema relatado na 0.3.4

O cursor parecia andar em pequenos avanços, como se tivesse resolução menor, embora continuasse preciso. A medição no celular físico confirmou duas causas, ambas introduzidas na 0.3.4:

1. **Travadas do TCP (algoritmo de Nagle).** O OkHttp não desliga o Nagle. Com a 0.3.4 enviando cada movimento na hora, o sistema às vezes segurava os pacotes pequenos esperando a confirmação do anterior: o PC ficava até 280 ms sem receber nada e depois recebia uma rajada de movimentos de uma vez. A 0.3.3 escapava disso porque enviava num ritmo fixo de 16 ms.
2. **Salto no início de cada deslizada.** O movimento feito dentro da zona de toque (enquanto ainda podia ser um clique) era guardado e enviado de uma vez ao virar movimento. O primeiro passo de cada deslizada tinha o dobro do tamanho normal; com o dedo real, que começa devagar, o cursor parava e depois dava um pulo.

## Correções

- O cliente abre o socket com `TCP_NODELAY` (`NoDelaySocketFactory` em `RemoteConnection.kt`). O agente já fazia isso do lado do PC.
- O movimento dentro da zona de toque volta a ser descartado, como na 0.3.3. O ajuste fino da 0.3.4 continua: dedo pousado por 300 ms passa a mover com qualquer deslocamento.

## Medição no celular físico

Celular Android 10 (tela de 60 Hz) no mesmo Wi-Fi do PC, pareado com um servidor de medição temporário que registrava o instante de chegada de cada movimento, sem mover o cursor. Foram feitas deslizadas automáticas de velocidade constante no touchpad (12 por rodada).

| Versão | Rodadas | Travadas acima de 100 ms | Movimentos em rajada (menos de 2 ms entre si) | Maior intervalo | 1º movimento da deslizada |
|---|---|---|---|---|---|
| 0.3.3 | 1 | 0 | 0% | 37 ms | 6,8 px |
| 0.3.4 | 3 | 9 | 7,2% | 280 ms | 13,3 px |
| 0.3.5 | 3 | 0 | 0,2% | 35 ms | 6,9 px |

O movimento típico por quadro foi de 6,8 px em todas as versões. Em telas de 90/120 Hz o envio é mais frequente e o efeito do Nagle tende a ser maior.

## Gestos novos do touchpad

- **Toque e toque-e-segure: arrastar.** Toque uma vez e, logo em seguida, toque de novo e segure: o botão esquerdo fica pressionado enquanto o dedo desliza. Soltar o dedo solta o botão.
- **Dois toques rápidos:** clique duplo.
- **Toque com dois dedos:** clique direito. A rolagem com dois dedos só começa depois da zona de toque, para o toque não rolar sem querer.

Para o PC não confundir o início de um arrasto com um clique duplo (o que abriria um arquivo ou maximizaria uma janela pela barra de título), o clique do toque simples sai depois de 250 ms, a janela do segundo toque. Os botões da tela continuam imediatos.

Ao perder o foco, trocar de tela ou cancelar o gesto, o botão pressionado pelo arrasto é solto.

## Verificações

- Testes JVM: 23 aprovados, incluindo `TCP_NODELAY` no cliente e as ações de arrasto, clique duplo e clique direito.
- Testes instrumentados no celular físico (Android 10): 15 aprovados e 1 opcional ignorado, incluindo o novo `TouchpadGestureTest` (toque adiado, toque-e-segure arrastando sem clique antes, clique duplo, clique direito com dois dedos e rolagem sem clique direito).
- Lint aprovado.
- O APK mantém o certificado de desenvolvimento das versões anteriores: instale por cima, sem desinstalar, para manter o pareamento.
