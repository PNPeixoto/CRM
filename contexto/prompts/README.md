# Pacote de prompts CRM PNP v3

Este manifesto é a fonte canônica da trilha principal/backend. A v3 substitui
qualquer pacote v1/v2 e o antigo `PROMPT-PROXIMA-SESSAO.md`, que permanece
somente como registro histórico.

A trilha canônica complementar de interface é
`frontend/manifest.yaml`, versão 4. Os identificadores qualificados evitam
colisão: `backend:05`, `frontend:F4` e, nas execuções repetíveis,
`frontend:F7:<dominio>`. Dependências entre trilhas são explícitas; nenhum dos
dois manifestos substitui o outro.

## Como usar

1. Confira a ordem e os pré-requisitos no manifesto da trilha aplicável.
2. Execute um prompt por branch/PR revisável.
3. Copie o prompt inteiro; ele contém o protocolo mínimo necessário e não
   depende de requisito escondido em versão anterior.
4. Só avance quando os critérios de aceite e o gate aplicável estiverem verdes.
5. Registre decisões em ADR individual e a sessão em arquivo com timestamp e
   slug da tarefa. Não reescreva o estado consolidado numa branch paralela.

O `PREAMBULO.md` é a referência editorial para manter os prompts coerentes. Os
prompts repetem as regras indispensáveis para continuarem autossuficientes.
O pacote de frontend possui preâmbulo próprio para os riscos do navegador e usa
os mesmos estados de execução.

## Estados do manifesto

- `ready`: definição pronta para execução;
- `in_progress`: execução ativa numa branch identificada;
- `blocked`: depende de decisão ou estado externo documentado;
- `completed`: critérios e evidências aprovados;
- `superseded`: substituído por outro prompt indicado no manifesto.

Alterar status é tarefa de integração/consolidação. Branches de implementação
não disputam o mesmo campo no manifesto.
