{{/* Base name, honouring nameOverride, truncated to 63 chars */}}
{{- define "sestacon.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "sestacon.fullname" -}}
{{- $name := include "sestacon.name" . -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "sestacon.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Common labels */}}
{{- define "sestacon.labels" -}}
helm.sh/chart: {{ include "sestacon.chart" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/part-of: {{ include "sestacon.name" . }}
{{- end -}}

{{/* Component selector labels. Call with (dict "ctx" . "component" "app") */}}
{{- define "sestacon.selectorLabels" -}}
app.kubernetes.io/name: {{ include "sestacon.name" .ctx }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{/*
Compose a full image reference from a per-component image block.
Call with (dict "ctx" . "image" .Values.postgres.image).
global.registry.url overrides the component registry when set.
*/}}
{{- define "sestacon.image" -}}
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
{{- define "sestacon.pullPolicy" -}}
{{- $g := .ctx.Values.global | default dict -}}
{{- .image.pullPolicy | default $g.imagePullPolicy | default "IfNotPresent" -}}
{{- end -}}

{{/* storageClass resolution: component value, else global, else omit */}}
{{- define "sestacon.storageClass" -}}
{{- $g := .ctx.Values.global | default dict -}}
{{- .sc | default $g.storageClass -}}
{{- end -}}

{{- define "sestacon.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "sestacon.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "sestacon.postgres.fullname" -}}
{{- printf "%s-postgres" (include "sestacon.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "sestacon.redis.fullname" -}}
{{- printf "%s-redis" (include "sestacon.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Resolve the postgres password secret name (generated or existing) */}}
{{- define "sestacon.postgres.secretName" -}}
{{- if .Values.postgres.auth.existingSecret -}}
{{- .Values.postgres.auth.existingSecret -}}
{{- else -}}
{{- printf "%s-secret" (include "sestacon.postgres.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "sestacon.redis.secretName" -}}
{{- if .Values.redis.auth.existingSecret -}}
{{- .Values.redis.auth.existingSecret -}}
{{- else -}}
{{- printf "%s-secret" (include "sestacon.redis.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "sestacon.pullSecrets" -}}
{{- $g := .Values.global | default dict -}}
{{- with $g.pullSecret }}
imagePullSecrets:
  - name: {{ . }}
{{- end }}
{{- end -}}
