"""Estado verificable por SHA para clientes que no exponen los check-runs de Actions.

Usa únicamente el token efímero de Actions; no imprime token, cabeceras ni cuerpos.
"""
import argparse
import json
import os
import re
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('state', choices=['pending', 'success', 'failure', 'cancelled'])
    args = parser.parse_args()
    repository = os.environ['GITHUB_REPOSITORY']
    sha = os.environ['GITHUB_SHA']
    run = os.environ['GITHUB_RUN_ID']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository) or not re.fullmatch(r'[0-9a-f]{40}', sha) or not run.isdigit():
        raise ValueError('Metadatos de CI no válidos')
    state = 'error' if args.state == 'cancelled' else args.state
    description = {'pending': 'Validación Android en curso', 'success': 'Compilación, JVM, Lint y seguridad correctos',
                   'failure': 'Validación fallida: consulta los informes del mismo SHA', 'error': 'Ejecución cancelada'}[state]
    payload = {'state': state, 'context': 'android/validacion', 'description': description,
               'target_url': f'https://github.com/{repository}/actions/runs/{run}'}
    request = urllib.request.Request(f'https://api.github.com/repos/{repository}/statuses/{sha}',
                                     json.dumps(payload).encode(), method='POST', headers={
                                         'Accept': 'application/vnd.github+json',
                                         'Authorization': 'Bearer ' + os.environ['PBT_CI_STATUS_TOKEN'],
                                         'User-Agent': 'PbtMetaGPT-CI', 'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status != 201:
            raise ValueError('No se pudo registrar el estado de CI')
    print('Estado de CI registrado para el SHA actual.')


if __name__ == '__main__':
    main()
