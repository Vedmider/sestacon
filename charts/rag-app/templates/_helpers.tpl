{{/* Base name, honouring nameOverride, truncated to 63 chars */}}
{{- define "rag-app.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "rag-app.fullname" -}}
{{- $name := include "rag-app.name" . -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "rag-app.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Common labels */}}
{{- define "rag-app.labels" -}}
helm.sh/chart: {{ include "rag-app.chart" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/part-of: {{ include "rag-app.name" . }}
{{- end -}}

{{/* Component selector labels. Call with (dict "ctx" . "component" "app") */}}
{{- define "rag-app.selectorLabels" -}}
app.kubernetes.io/name: {{ include "rag-app.name" .ctx }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{/*
Compose a full image reference from a per-component image block.
Call with (dict "ctx" . "image" .Values.postgres.image).
global.registry.url overrides the component registry when set.
*/}}
{{- define "rag-app.image" -}}
{{- $g := .ctx.Values.global | default dict -}}
{{- $gReg := "" -}}
{{- if $g.registry -}}{{- $gReg = $g.registry.url -}}{{- end -}}
{{- $registry := $gReg | default .image.registry -}}
{{- $repo := .image.repository -}}
{{- $name := .image.name -}}
{{- $tag := .image.tag | default .ctx.Chart.AppVersion -}}
{{- $path := "" -}}
{{- if $repo -}}
{{- $path = printf "%s/%s/%s:%s" $registry $repo $name $tag -}}
{{- else -}}
{{- $path = printf "%s/%s:%s" $registry $name $tag -}}
{{- end -}}
{{- print (regexReplaceAll "/+" $path "/") -}}
{{- end -}}

{{/* Per-component imagePullPolicy, honouring global override */}}
{{- define "rag-app.pullPolicy" -}}
{{- $g := .ctx.Values.global | default dict -}}
{{- .image.pullPolicy | default $g.imagePullPolicy | default "IfNotPresent" -}}
{{- end -}}

{{/* storageClass resolution: component value, else global, else omit */}}
{{- define "rag-app.storageClass" -}}
{{- $g := .ctx.Values.global | default dict -}}
{{- .sc | default $g.storageClass -}}
{{- end -}}

{{- define "rag-app.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "rag-app.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "rag-app.postgres.fullname" -}}
{{- printf "%s-postgres" (include "rag-app.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "rag-app.redis.fullname" -}}
{{- printf "%s-redis" (include "rag-app.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Resolve the postgres password secret name (generated or existing) */}}
{{- define "rag-app.postgres.secretName" -}}
{{- if .Values.postgres.auth.existingSecret -}}
{{- .Values.postgres.auth.existingSecret -}}
{{- else -}}
{{- printf "%s-secret" (include "rag-app.postgres.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "rag-app.redis.secretName" -}}
{{- if .Values.redis.auth.existingSecret -}}
{{- .Values.redis.auth.existingSecret -}}
{{- else -}}
{{- printf "%s-secret" (include "rag-app.redis.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "rag-app.pullSecrets" -}}
{{- $g := .Values.global | default dict -}}
{{- with $g.pullSecret }}
imagePullSecrets:
  - name: {{ . }}
{{- end }}
{{- end -}}
