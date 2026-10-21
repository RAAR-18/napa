import { render } from '@testing-library/react-native';

import App from './App';

describe('App', () => {
  it('shows the Ñapa brand and purpose', async () => {
    const { getByRole, getByText } = await render(<App />);

    expect(getByRole('header')).toBeTruthy();
    expect(getByText('Ñapa')).toBeTruthy();
    expect(
      getByText('Conecta a vendedores locales con clientes cercanos.'),
    ).toBeTruthy();
  });
});
