# GtheDay

Aplicativo Android pessoal para acompanhar alimentação e treino. Os registros ficam no aparelho.

## Instalação

1. Baixe o arquivo GtheDay.apk na página de versões e abra-o no Android 8.0 ou posterior.
2. Se o Android pedir, autorize a instalação para o aplicativo que abriu o arquivo.
3. Para receber lembretes, abra Configurações no GtheDay e permita notificações. Para avisos no horário, permita também alarmes exatos se a opção aparecer.

O app usa horários diários predefinidos. Configurações mostra os horários e permite desligar cada aviso. Água tem um aviso diário, com registro de conclusão.

Retirar o app da lista de recentes faz parte do uso normal. Depois de usar **Forçar parada** nas configurações do Android, abra o GtheDay novamente para retomar os avisos. Em aparelhos com regras próprias de bateria, verifique a entrega com a tela bloqueada e após reiniciar.

## Compilação e validação

Com JDK 17, SDK Android 35 e Gradle 8.11.1:

    gradle :app:testReleaseUnitTest :app:assembleRelease

O fluxo do GitHub Actions assina o APK, verifica sua assinatura e testa o arquivo em um emulador Android antes de publicar a versão. O teste altera o relógio do emulador e deve ser executado em um Android de teste. Chaves e senhas ficam fora do código-fonte.
