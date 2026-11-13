import { render } from '@testing-library/react-native';

import WelcomeContent from './src/components/WelcomeContent';

describe('WelcomeContent', () => {
  it('shows the Ñapa brand and purpose', async () => {
    const { getByRole, getByText } = await render(<WelcomeContent />);

    expect(getByRole('header')).toBeTruthy();
    expect(getByText('Ñapa')).toBeTruthy();
    expect(
      getByText('Conecta a vendedores locales con clientes cercanos.'),
    ).toBeTruthy();
  });
});
