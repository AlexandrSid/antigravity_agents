#!/usr/bin/env pwsh
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$scriptDir\run.py" @args
