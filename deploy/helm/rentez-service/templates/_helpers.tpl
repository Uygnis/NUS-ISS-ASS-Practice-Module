{{/*
Shared naming and labels. The release name is the service name, so
`helm upgrade --install account-service ...` produces resources called
account-service and nothing needs a name prefix.
*/}}

{{- define "rentez.name" -}}
{{- required "serviceName must be set - use a file from deploy/helm/values/" .Values.serviceName -}}
{{- end -}}

{{- define "rentez.labels" -}}
app.kubernetes.io/name: {{ include "rentez.name" . }}
app.kubernetes.io/part-of: rentez
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "rentez.selectorLabels" -}}
app.kubernetes.io/name: {{ include "rentez.name" . }}
{{- end -}}

{{/*
Fully-qualified image reference. Both halves are injected by `make aws-up`:
the registry from the persistent stack's output, the tag from the git SHA that
CI built. Failing loudly here beats deploying whatever `:latest` happens to be.
*/}}
{{- define "rentez.image" -}}
{{- $registry := required "image.registry must be set (make aws-up injects it)" .Values.image.registry -}}
{{- $tag := required "image.tag must be set - never deploy a floating tag" .Values.image.tag -}}
{{- printf "%s/rentez-%s:%s" $registry (include "rentez.name" .) $tag -}}
{{- end -}}
