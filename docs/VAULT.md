# Segredos no HashiCorp Vault (stage)

Em stage, o gateway le `SECURITY_USER`, `SECURITY_PASS` e `GATEWAY_CONSUMERS_B64` do Vault, no boot, em vez de variaveis
do Portainer. O perfil e `vault` (`SPRING_PROFILES_ACTIVE=staging,vault`); sem ele (dev local e testes) nada muda.

## Como funciona

- Autenticacao por **AppRole** (`api-gateway-staging`), com politica de **leitura apenas** em
  `secret/data/api-gateway/staging` (KV v2).
- Cada chave do segredo vira uma propriedade com o proprio nome, que o `application.yml` ja referencia
  (`${SECURITY_USER}`, `${SECURITY_PASS}`, `${GATEWAY_CONSUMERS_B64}`).
- A unica credencial que continua no Portainer e o AppRole: `VAULT_ROLE_ID` e `VAULT_SECRET_ID` (Environment da stack).
- `VAULT_ADDR` padrao: `http://vault.interno:8200`. O nome `vault.interno` aponta para o host (`extra_hosts: host-gateway`),
  onde a stack `vault` publica a 8200. Nao ha rede externa entre as stacks.
- **fail-fast**: Vault inacessivel, selado ou AppRole invalido = o gateway nao sobe (melhor que subir sem autenticacao).
  Depois de reiniciar o host ou o Vault, faca o unseal antes de subir/reiniciar o gateway.
  Instancias ja em execucao nao releem o Vault: so leem no boot.

## Configuracao no Vault (uma vez)

Com um token de administrador (nao o root do dia a dia):

```bash
vault secrets enable -path=secret kv-v2          # se ainda nao existir
vault auth enable approle                         # se ainda nao existir

vault policy write api-gateway-staging - <<'POLICY'
path "secret/data/api-gateway/staging" {
  capabilities = ["read"]
}
POLICY

vault write auth/approle/role/api-gateway-staging \
  token_policies=api-gateway-staging token_ttl=1h token_max_ttl=4h

vault kv put -mount=secret api-gateway/staging \
  SECURITY_USER=... SECURITY_PASS=... GATEWAY_CONSUMERS_B64=...

vault read  -field=role_id   auth/approle/role/api-gateway-staging/role-id
vault write -f -field=secret_id auth/approle/role/api-gateway-staging/secret-id
```

Coloque `role_id` e `secret_id` em `VAULT_ROLE_ID` e `VAULT_SECRET_ID` na stack `dev_springboot_gateway`
e remova `SECURITY_USER`, `SECURITY_PASS` e `GATEWAY_CONSUMERS_B64` do Environment.

## Trocar um segredo ou o cadastro de consumidores

```bash
vault kv patch -mount=secret api-gateway/staging GATEWAY_CONSUMERS_B64="$(base64 -w0 consumers.yml)"
```

Depois, "Pull and redeploy" da stack (ou reinicie as instancias) para o gateway reler.

## Rotacionar o AppRole

`vault write -f auth/approle/role/api-gateway-staging/secret-id` gera um novo `secret_id`; atualize no Portainer,
redeploye e revogue o antigo (`secret-id-accessor/destroy`).
