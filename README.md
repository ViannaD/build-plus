# Build+

Mod Fabric para Minecraft **1.20.1** que permite voo temporário limitado a uma
área configurável (o **Building Block**), para facilitar construções grandes
no Survival sem transformar o jogo em Criativo permanente.

## CI (GitHub Actions)

O workflow `.github/workflows/build.yml`:

- Roda em push/PR para `main` e também manualmente (`workflow_dispatch`).
- Usa `gradle/actions/setup-gradle` para provisionar o Gradle **sem precisar
  do wrapper commitado** (já que este projeto não inclui `gradlew`).
- Compila (`gradle build`), roda os testes (se houver) e sobe o `.jar` gerado
  como artefato do run.
- Se você empurrar uma tag no formato `v1.0.0`, ele cria automaticamente um
  **Release** no GitHub com o `.jar` já anexado.

Para lançar uma versão:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Se preferir usar o wrapper (`./gradlew`) em vez do Gradle provisionado pela
action, rode `gradle wrapper` localmente uma vez, commite a pasta `gradle/` e
o `gradlew`/`gradlew.bat`, e troque o passo "Setup Gradle" do workflow por
`gradle/actions/setup-gradle@v4` sem o `gradle-version` (ele detecta o
wrapper automaticamente) — ou simplesmente rode `./gradlew build`.

## Como compilar

Pré-requisitos: **JDK 17** e conexão com a internet (o Gradle/Fabric Loom
baixa Minecraft, mappings Yarn e a Fabric API na primeira build).

```bash
# Linux/macOS
./gradlew build

# Windows
gradlew.bat build
```

O `.jar` final fica em `build/libs/build-plus-1.0.0.jar`. Esse projeto **não
tem o wrapper do Gradle (`gradlew`) incluso** — abra a pasta no IntelliJ IDEA
com o plugin do Fabric/Loom, ou rode `gradle wrapper` uma vez para gerá-lo,
antes do primeiro build.

## O que já está implementado

- **Building Block**: bloco com hitbox composta (VoxelShape com vários cubos),
  luminoso, que ao ser clicado abre a GUI de Modo Construção.
- **GUI custom** (`BuildingBlockScreen`): botões `[-] tamanho [+]` (100–500),
  lista de jogadores permitidos com botão `+` (abre lista de jogadores
  online), botões `Iniciar` / `Finalizar`.
- **Holograma (wireframe)**: desenhado em tempo real enquanto a GUI está
  aberta, mostrando exatamente os limites da área, mudando de azul → amarelo
  → vermelho conforme a proximidade da borda.
- **Sistema de voo por área**: ao iniciar, todos os jogadores autorizados
  online ganham voo (`allowFlying`/`flying`), sem dano de queda, restrito ao
  cubo centrado no bloco.
- **Contagem regressiva de 5s** ao sair da área, com mensagens de aviso e
  encerramento automático da participação do jogador se não retornar; se
  voltar a tempo, recebe "Área recuperada." e o timer é cancelado.
- **Multiplayer real**: cada Building Block guarda dono, lista de jogadores,
  tamanho e estado (ativo/inativo) — vários blocos funcionam em paralelo sem
  interferir uns nos outros.
- **Persistência**: tudo é salvo no NBT do Block Entity (`BuildingBlockEntity`).
- **Segurança**: bloqueia quebrar o Building Block enquanto ativo (e encerra a
  sessão automaticamente caso seja destruído por outro meio, ex. explosão);
  cancela dano de queda; força o jogador a acordar/parar de planar de elytra
  se estiver dentro do Modo Construção.
- **Resumo ao finalizar**: tempo total, número de jogadores, área e uma
  contagem heurística de blocos colocados.

## O que é scaffold/placeholder e precisa de atenção

- **Modelo 3D e textura**: já usando o seu modelo customizado do Blockbench
  (`models/block/building_block.json`) e a sua textura
  (`textures/block/building_block.png`, 64x64). A hitbox (`BuildingBlock.java`)
  foi simplificada para o cubo principal 16x16x16 — os detalhes decorativos
  finos que saem do modelo não têm colisão própria (padrão comum para
  decorações). Ajuste a `VoxelShape` se quiser colisão mais fiel a alguma
  parte específica do modelo.
- **Bloqueio de foguetes de fogos de artifício**: o hook de segurança está
  preparado (`SafetyEvents.isRestricted`), mas o cancelamento do *impulso* do
  foguete durante o voo geralmente exige um Mixin em `FireworkRocketItem`/
  `FireworkRocketEntity` (a API pública do Fabric não expõe um callback
  "antes de usar item" que cubra 100% dos casos). Deixei o método utilitário
  pronto para você plugar um Mixin se quiser reforçar 100%.
- **Melhorias da seção "Melhorias interessantes"** do spec (construção
  fantasma, medidor de altura, régua, grid `G`, linhas centrais X/Z, botão de
  retorno ao cair) **não foram implementadas** nesta primeira versão — o
  código já está organizado (pacotes `client.render`, `session`) para você
  plugar cada uma incrementalmente.
- **Wireframe**: usa `RenderLayer.getLines()` + `VertexConsumer` (API estável
  do Minecraft) em vez de mexer direto em `com.mojang.blaze3d.vertex` — essa
  segunda abordagem chegou a ser usada numa versão anterior e **quebrou a
  build no CI** (`package com.mojang.blaze3d.vertex does not exist`), então
  foi trocada por esta.

## Estrutura

```
src/main/java/com/buildplus/
├── BuildPlusMod.java            # entrypoint comum (main)
├── block/
│   ├── BuildingBlock.java       # bloco + hitbox + onUse
│   ├── BuildingBlockEntity.java # dados persistentes (NBT)
│   └── ModBlocks.java           # registro
├── network/
│   └── NetworkHandler.java      # pacotes C2S/S2C
├── session/
│   ├── BuildSession.java        # estado em memória de uma sessão ativa
│   ├── BuildSessionManager.java # voo, área, contagem regressiva, tick
│   └── SafetyEvents.java        # regras de segurança
└── client/
    ├── BuildPlusClient.java     # entrypoint cliente
    ├── gui/
    │   ├── BuildingBlockScreen.java
    │   └── PlayerPickerScreen.java
    └── render/
        └── WireframeRenderer.java
```

## Licença

MIT — adicione um arquivo `LICENSE` antes de publicar no GitHub, se quiser
manter essa licença ou trocar por outra.
