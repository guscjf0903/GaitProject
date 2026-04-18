{{- define "gait.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "gait.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s" (include "gait.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "gait.labels" -}}
app.kubernetes.io/name: {{ include "gait.name" . }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "gait.backend.name" -}}
{{- printf "%s-backend" (include "gait.fullname" .) -}}
{{- end -}}

{{- define "gait.frontend.name" -}}
{{- printf "%s-frontend" (include "gait.fullname" .) -}}
{{- end -}}

{{- define "gait.backend.configmapName" -}}
{{- printf "%s-backend-config" (include "gait.fullname" .) -}}
{{- end -}}

{{- define "gait.backend.secretName" -}}
{{- if .Values.backend.secret.existingSecret -}}
{{- .Values.backend.secret.existingSecret -}}
{{- else -}}
{{- printf "%s-backend-secret" (include "gait.fullname" .) -}}
{{- end -}}
{{- end -}}
